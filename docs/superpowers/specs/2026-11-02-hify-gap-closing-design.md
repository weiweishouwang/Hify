# 缺口补齐：韧性层、能力连通、控制台两页

> 2026-11-02 · 状态：已实施
>
> 这不是新模块，是**对着 CLAUDE.md 的需求逐条核对之后补上欠的实现**。
> 编号约定同其他规格文档：`§x.y` 指 CLAUDE.md 的章节。

---

## 一、范围

按「需求 → 实现」的差距清单，依次补四件事：

| # | 缺口 | 依据 |
|---|---|---|
| 1 | LLM 调用链的韧性层整层没接线 | §3.5 |
| 2 | 连通性测试只有协议连通，缺能力连通 | §3.5 |
| 3 | 控制台缺日志页 | §1.2 |
| 4 | 控制台缺设置页 | §1.2 |

**没做的**：认证与 API Key（§五 待确认，方案未定）、「每个 Agent 一个可分享 Web App」
（依赖认证方案 —— 没有认证就等于开了匿名读写通道）。

---

## 二、韧性层接线（缺口 1）

### 2.1 接在哪

**接在 `hify-ai` 的 `ChatServiceImpl.stream`** —— 模型 → 接入点 → 适配器解析完之后，
真正发上游请求的那一层。三个协议适配器都从这里走，所以接一次就都覆盖了。

**为什么不在适配器里接**：接一次要改三个适配器 + 工厂；接在服务里只有一处。

### 2.2 熔断的粒度是「接入点实例」，不是协议档位

**决定**：熔断器的 key 用 `provider.getPublicId()`。

**为什么**：两个不同的 OpenAI 兼容端点应该各自独立 —— 一个挂了不能把另一个也断开。
按协议档位（openai / anthropic / ollama）分组的话，同一个 openai 档位下的
五个接入点会共用一个熔断器，其中一个 key 失效会把其余四个一起熔掉。

**代价**：`CircuitBreakerService` 的 javadoc 原来举的例子是 `openai` 这样的协议名，
现在要按实例名传。

### 2.3 流式只在吐出内容之前重试

**决定**：`LlmRetryPolicy` 新增 `executeStreaming(action, producedOutput)`，
`producedOutput` 回答「这次调用已经产出过内容了吗」。

**为什么**：已经吐给用户的文本，重试会带来**重复内容**。宁可让这次流失败，
也不能让用户看到同一段话出现两次。

**做法**：`ChatServiceImpl` 包一层回调，文本与工具调用都算「产出」——
工具调用是上游明确表达的意图，重试会丢掉它。

### 2.4 每用户每 provider 在途上限

**决定**：`ChatRequest` 加一个 `userId` 字段（显式携带，不靠 ThreadLocal），
`ChatServiceImpl` 在调用前占名额、finally 里释放。

**上限 3，键 `hify:llm:inflight:{userId}:{providerPublicId}`，兜底 TTL 120 秒**
（部署规格 §13.4 的约定）。

**为什么 user id 要显式带**：编排跑在 `llmExecutor` 上，那边的
`CurrentUserHolder` 是空的（§3.1 C5：线程池复用会串号）。conversation 与 workflow
两处编排都已经把 user id 显式传进 `ChatRequest`。

**没有 user id 时不限流**：后台任务（索引、目录同步）不该算到某个人头上。

### 2.5 shared 侧的三个新能力

| 类 | 新增 | 为什么 |
|---|---|---|
| `LlmRetryPolicy` | `executeStreaming(Runnable, BooleanSupplier)` | 流式的重试判定与同步的不同 |
| `CircuitBreakerService` | `executeStream(String, Runnable, BooleanSupplier)` | 熔断在外、重试在内，流式版 |
| `RateLimiter` | `tryAcquireInFlight` / `releaseInFlight` | 在途计数要**释放**，固定窗口计数不用 |

**`RateLimiter` 原本只有固定窗口计数**（用完不释放），与它 javadoc 里写的
「限制并发在途数」对不上 —— 现在补上真正用在途计数的那两个方法，
两个语义在 javadoc 里分开写清楚。

**在途计数的 Lua 要回滚**：超限时把刚加的那个减回去，否则多出来的那一个
会一直占着名额直到 TTL 到期，用户再也发不出请求。

### 2.6 健康探测不走在熔断器后面

**决定**：熔断只接在对话调用上，**目录探测与健康检查不接**。

**为什么**：熔断打开时，探测必须还能打出去 —— 否则探测结果会被熔断状态污染，
「熔断打开」与「接入点真的挂了」分不开。

---

## 三、能力连通（缺口 2）

### 3.1 怎么测

给模型一个只有一个布尔参数的探针工具，明确要求它调用这个工具；
上游回了 `tool_calls` 才算通过。

**测的是真实链路** —— 走 `ChatService`，跟生产对话同一条路，
顺带把重试、熔断、在途限流都跑一遍。

### 3.2 三种结果要分清

| 结果 | 含义 |
|---|---|
| 通过 | 上游回了工具调用 |
| 不通过 | 上游回了文字 —— tools 定义被静默丢弃（Ollama 的 `/v1` 兼容层就是这样） |
| 没测 | 接入点下没有启用的对话模型 |

**「上游报错」与「上游不支持」要分开**：前者是测不了，后者是能力不行。
提示语不同，用户才知道下一步该做什么。

### 3.3 接口

`ProviderConnectionTestResp` 加四个字段：`capabilitySupported`（null = 没测）、
`capabilityMessage`、`capabilityModel`、`capabilityLatencyMs`。

前端按结果分三种提示：能力不通用 warning（这是最坑的一种 ——
协议是通的，但 Agent 的工具循环是坏的）。

---

## 四、控制台两页（缺口 3、4）

### 4.1 日志页：两个页签，各走各的接口

- **对话记录** → `GET /api/v1/conversations/runs`
- **工作流记录** → `GET /api/v1/workflows/runs`

**为什么不做成一个聚合接口**：运行记录分属两个模块，各自有表。
要在 app 层聚合，就得让两个模块先各自暴露列表方法，再在 app 层合并两路游标 ——
合并两路游标分页很麻烦（下一页的游标有两个）。**两个页签各自翻页更简单**，
也让模块边界保持干净（前端直接调各自的模块）。

**对话运行记录带上会话标题与 Agent 名字**：会话在同一模块内批量取；
Agent 按 id 去重后逐个取（一页里通常只有一两个 Agent，不是 N+1）。

**工作流运行记录带入参出参摘要**：可能很大，截断到 200 字符。

### 4.2 设置页：只读

回答三个问题：跑的是哪个版本、依赖通不通、关键的运行参数是多少。

- **版本与构建时间**：从 jar 里的 `META-INF/build-info.properties` 读
  （`spring-boot-maven-plugin` 的 build-info goal 生成）—— 版本号从包自身生成，
  不存在两处漂移
- **依赖状态**：MySQL / PostgreSQL / Redis 各探一次。
  **探不通一个只影响它自己那一行** —— 依赖状态是给人看的，
  有一个不通恰恰是要显示出来的
- **运行参数**：熔断参数、在途上限、超时、线程数等，「看一眼就知道行为」的那些

**向量库的探测直接用应用自己的连接参数**，不碰别的模块的内部 bean ——
跨模块 import `internal` 包会被 ArchUnit 拦下。

**一期没有可改的配置**：认证方案还没定（§五），也没有多环境切换 ——
有可改的东西再加写入接口。

---

## 五、验证

| 层 | 验证方式 |
|---|---|
| shared | `LlmRetryPolicy` / `CircuitBreakerService` / `RateLimiter` 各加针对性用例 |
| ai | `ChatServiceImplTest`（在途上限、熔断 key、跳过限流）、`ProviderCapabilityTestTest` |
| conversation / workflow | 运行记录列表的用例 |
| app | `SettingsServiceTest`（探不通一个依赖不影响其他） |
| 前端 | 日志页与设置页的组件测试；能力连通的三种提示 |
| 真实环境 | 真实模型跑一轮对话与工作流 → 日志页出现记录；能力连通返回「工具调用正常」；设置页三个依赖全绿 |

---

## 六、实现期发现

### 6.1 `Supplier<Void>` 接不住 void 的 lambda

**症状**：`executeStream` 的参数用 `Supplier<Void>`，传进去的 lambda 体是 void 调用，
编译报「void 无法转换为 Void」。

**原因**：`Supplier<Void>` 要求 lambda 是**值兼容**的 —— 块 lambda 必须有返回值，
`return;` 不行。

**做法**：流式调用本来就不需要返回值，把参数类型改成 `Runnable`，
熔断器用 `executeRunnable`（`CircuitBreaker` 有这个方法）。

### 6.2 能力测试要真的走生产链路

**做法**：能力测试调的是 `ChatService` 而不是适配器 —— 顺带把韧性层也跑一遍。
如果直接调适配器，测的就只是协议，与生产走的不是同一条路。

### 6.3 前端表格在 happy-dom 里要桩掉 ElSelect

**症状**：日志页的组件测试报「Maximum recursive updates exceeded in component <ElSelect>」。

**原因**：表格的「每页条数」选择器是 `el-select`，在 happy-dom 下会无限递归更新。
ProviderList 的测试早就踩过这个坑（那里桩掉了 `ElSelect` / `ElOption`），
新页面沿用同样的桩。
