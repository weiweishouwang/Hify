# hify-conversation 模块设计规格

> 阶段 0（咨询模式）的产出物。日期：2026-11-01 · 状态：已确认，直接进入实施
>
> **编号约定**：本文里 `§1.2` / `§2.1` / `§3.5` 指 **CLAUDE.md** 的章节；
> `§L6` / `§M6` 指 `docs/superpowers/specs/2026-10-29-hify-code-organization.md` 的条目；
> `§一` / `§二` 指本文自身的章节。

---

## 一、需求

### 1.1 模块定位

按 CLAUDE.md §2.1，`hify-conversation` 负责 **会话、消息、流式对话编排**，归属表
`conv_session`、`conv_message`、`conv_run`、`conv_message_citation`。
依赖 `shared`、`ai`、`tool`、`knowledge`、`agent`。

CLAUDE.md §2.1 定的运行时调用链，本模块全部要实现：

```
用户提问
 → conversation：读 Agent 配置（agent.api）
 → conversation：调 knowledge 检索（knowledge.api），拿到 chunk
 → conversation：调 ai 流式补全（ai.api）
 → 模型请求工具 → conversation 调 tool 执行（tool.api，MCP 来源走同一接口）
 → 工具结果回灌模型 → 直到模型不再要工具
 → conversation：写 conv_message + conv_run + conv_message_citation（引用 chunk 时）
```

一期交付：

| # | 交付物 | 说明 |
|---|---|---|
| 1 | 会话管理 | 创建（绑定 Agent）、列表、详情、改标题、归档、删除 |
| 2 | 消息历史 | 按会话列出消息（含工具调用与工具结果） |
| 3 | 流式对话 | `POST /api/v1/conversations/{id}/messages` → SSE |
| 4 | 工具调用循环 | 模型要工具 → 执行 → 回灌 → 再调模型，直到模型不再要工具 |
| 5 | 知识库检索 | 按 Agent 绑的知识库检索，片段注入上下文 |
| 6 | 引用记录 | `conv_message_citation` 存引用文本快照 |
| 7 | 运行记录 | `conv_run`：token、耗时、工具调用次数、错误 |
| 8 | 对话页 | 会话列表 + 消息流 + 输入框 + 工具调用中间态 |

### 1.2 明确不做

- **认证 / 登录** —— CLAUDE.md §五 认证方式还没定。本模块只留好接缝（§2.7），不实现登录
- **多分支会话树** —— 一期一个会话一条线
- **消息编辑 / 重生成 / 分支** —— 一期只追加
- **语音 / 图片多模态输入** —— 一期只支持文本
- **工作流** —— 那是 `hify-workflow`，两个编排层平行、互不依赖

---

## 二、选型（决定了什么 / 为什么 / 替代方案）

### 2.1 先给 `hify-ai` 补「流式对话」能力 —— 这是本模块的前置依赖

**决定**：在 `hify-ai` 的 api 包新增 `ChatService`，配三套协议适配器
（OpenAI 兼容 / Anthropic Messages / Ollama 原生）。

**为什么**：`hify-ai` 目前只有「接入点与模型的 CRUD」和「embedding」，**没有 chat**。
CLAUDE.md §2.1 明确「`hify-ai` 必须是纯能力（配置 CRUD、纯文本/流式补全、embedding），
任何组合逻辑都不许出现在 ai」。模型调用归 ai，conversation 只编排 —— 不这么做的话
要么 ai 依赖 conversation（成环），要么 conversation 自己拼协议（三套协议写三遍）。

**代价**：`hify-ai` 明显变大。**这是对的** —— 协议差异属于「能力层」的知识，
放在编排层意味着 workflow 也要再拼一遍。

**替代方案**：conversation 直接调 `shared.http.LlmHttpClient` 自己拼协议 —— 已否决，
三套协议写三遍，且 workflow 要重写。

### 2.2 SSE 形状 —— Spring MVC + `SseEmitter`，事件名沿用前端已定的契约

**决定**：controller 返回 `SseEmitter`，`produces = text/event-stream`。
事件名：`message` / `tool_call` / `tool_result` / `done` / `error`，末尾补一个 `[DONE]`。

**为什么**：CLAUDE.md §3.4 已定「用 Spring MVC，不引入 WebFlux」；前端
`hify-web/src/api/chat.ts` 已经把事件名与 payload 字段写死了（`{content}` /
`{id,name,arguments}` / `{id,result}` / `{finishReason}` / `{code,message}`）——
后端按它对齐，前端一行不改。

**代价**：SSE 的分帧由 Spring 负责，不能自己控制 `data:` 里的换行 ——
所以 payload 一律用 JSON，绝不塞裸文本。

### 2.3 线程模型 —— 编排与写出分两条线程

**决定**：

- **编排线程** = `llmExecutor`：调上游模型、执行工具、写库，全部在这条线程上
- **写出线程** = 新增的 `sseWriterExecutor`：只负责把事件写进 `SseEmitter`
- 两者之间用一个**有界队列**（容量 256）交接

**为什么**：CLAUDE.md §3.4 写死了「**往浏览器写帧的线程必须和 llmExecutor 分开**：
一个慢客户端的阻塞写如果占住 llmExecutor 线程，就等于吃掉一个 LLM 并发名额」。
上游读是阻塞的（要等模型），浏览器写也是阻塞的（客户端可能慢），两者必须分开。

**队列满了怎么办 —— 背压**：编排线程 `put` 阻塞，等写出线程腾出空间。
**代价**：客户端极慢时，编排线程会被拖住，等于占住一个 LLM 名额。
**替代方案**：

- 丢弃事件 —— 已否决，丢的是模型输出，用户看到的话断断续续
- 无界缓冲 —— 已否决，100 个慢客户端能把内存打满

背压是唯一不丢内容、也不无界增长的选项。慢客户端是异常情况，
正常客户端的写出速度远快于模型的产出速度。

**写出池的规格**：`ThreadPoolExecutor(48, 200, 60s, SynchronousQueue, sse-write-, AbortPolicy)`。

- 核心 48：与 CLAUDE.md 给的 `boundedElastic(48, 1000)` 同一量级
- `SynchronousQueue`（队列长度 0）：超过核心线程数立刻新建线程，最多到 200 ——
  与 boundedElastic 的弹性语义一致
- `AbortPolicy`：超过 200 条并发流时**快速失败**（明确报错「并发连接过多」），
  不用 CallerRuns —— 那会让写出回落到编排线程上，等于绕回我们要避免的耦合

### 2.4 工具调用循环放在 conversation

**决定**：循环在 conversation 模块：模型要工具 → 调 `tool.api` 执行 → 把结果作为
`tool` 角色消息回灌 → 再调模型 → 直到模型不再要工具。**最多 5 轮**。

**为什么**：CLAUDE.md §2.1 的运行时调用链就是这么画的（conversation 调 tool 执行、
工具结果回灌模型）。ai 只管调模型，tool 只管执行工具，**组合逻辑在编排层**。

**代价**：ai 的 `ChatMessage` 要能表达「assistant 带工具调用」和「tool 结果」两种形态 ——
协议差异由 ai 的适配器收敛，conversation 不感知。

**上限 5 轮**：防止模型反复要同一个工具把额度烧光。超限就以「达到工具调用轮次上限」结束，
并把已经拿到的内容交给用户。

### 2.5 知识库检索 —— 每个绑定的知识库各检一次，片段注入 system 提示词

**决定**：Agent 绑了几个知识库就检索几次，每次 `topK=3`，把命中片段拼成
「参考资料」段落追加到 system 提示词后面。命中的 chunk 逐条写进
`conv_message_citation`（连文本快照一起存）。

**为什么**：CLAUDE.md §3.3 定了「引用关系存文本快照 —— 知识库重新索引后 chunk id 会变，
历史对话的溯源不能依赖 PG 还在」。

**代价**：检索是同步的（向量化 + PG 检索），会拖慢首字时间。
知识库为空时跳过，不阻塞对话。

### 2.6 上下文有界

**决定**：送给模型的历史消息**只带最近 20 条**；检索片段按**字符预算 4000**裁剪；
单条用户输入最长 4000 字符。

**为什么**：CLAUDE.md §3.5「上下文必须有界：`max-context-tokens: 16384`」。

**代价**：很长的会话会丢掉早期的上下文 —— 一期不做上下文压缩。

### 2.7 会话归属 —— 一期没有认证，落「开发用户」

**决定**：`conv_session.user_id` 是 NOT NULL 外键指向 `user.id`，而**认证方案还没定**
（CLAUDE.md §五），登录接口不存在。做法：

- 在 `hify-shared` 的 api 包加 `CurrentUserService.currentUserId()`
- 实现：先取 `CurrentUserHolder`（将来的登录拦截器会往这里塞用户）；
  取不到就落到一个内置的**开发用户**（`user.username = 'developer'`），不存在就幂等创建
- conversation 模块所有会话都归它

**为什么**：不这么做的三条路都不通 ——

- 把 `user_id` 改成可空：会话就没有归属，「谁的会话」这个问题被回避而不是被回答
- 在 conversation 模块直接查 `user` 表：违反 §3.2「跨模块只走对方的 api 包」
- 自己实现登录：认证方案还没定（§五），现在定死等于替用户做了决定

**代价**：一期所有会话看起来属于同一个人。**将来接上登录只要让登录拦截器往
`CurrentUserHolder` 里塞用户，这里一行都不用改。**

**代价**：开发用户这行数据会被写进 `user` 表 —— 它是 `user` 表自己的数据，
由 shared 负责，conversation 不碰。

### 2.8 事件流表是分区表，主键是 `(id, created_at)`

**决定**：`conv_message` / `conv_run` / `conv_message_citation` **不继承 `BaseEntity`**
（没有 `deleted`、没有 `created_by`），自己声明 `id` / `createdAt` / `updatedAt`。
`conv_session` 是配置表，**继承 `BaseEntity`**（有 `deleted`、有 `created_by`）。

**为什么**：CLAUDE.md §3.3「事件流表不继承本类：事件流表是分区表，主键是
`(id, created_at)` 复合主键，且分区表的唯一键必须包含分区列，因此无法使用本类的
单列主键」+「`@TableLogic` 会给每条 SQL 追加 `deleted = 0`，破坏分区裁剪」。

**代价**：事件流表的实体不能用 `BaseMapper.deleteById`（没有 deleted 列）——
本来也不需要删。

### 2.9 数据模型不需要改

**核对结论**：`conv_session` / `conv_message` / `conv_run` / `conv_message_citation` 四张表的
字段已经够用，`schema.sql` 里也**已经带上了按月分区的定义**
（`PARTITION BY RANGE COLUMNS(created_at)`，月度分区 + 一个 `pmax` 兜底）。

- **工具调用消息存 `content_type='json'` 的正文** —— 表上有 `content_type`（text / markdown / json），
  这一列本来就是给结构化正文准备的，不用加列
- **引用快照存 `quote_text`** —— 「引用关系存文本快照」已经定过（CLAUDE.md §3.3）

**代价**：没有代价。**收益**：事件流表是分区表，加列要重建整张表 —— 能不加就不加。

---

## 三、数据模型

### 3.1 `conv_session` —— 不动

`public_id` / `user_id` / `agent_id` / `api_key_id` / `title` / `message_count` /
`last_message_at` / `status`（active / archived）/ 审计字段全部保留。

**不加 `config_version`**：会话不是配置，没有「两个管理员同时改会互相覆盖」的问题。
状态只有 active / archived 两个值，冲突了也没多大关系。

### 3.2 `conv_message` —— 不动

角色 `user` / `assistant` / `system` / `tool`；`content_type` 固定 `text`
（一期只支持文本）；`model_id` / `provider_id` 是逻辑引用。

### 3.3 `conv_run` —— 不动

一轮问答一行：输入输出、工具调用次数、token 三项、错误码与错误原因、总耗时。

### 3.4 `conv_message_citation` —— 不动

`chunk_id` + `doc_id` + `chunk_index` + `kb_id` + `quote_text`（文本快照）+ `score`。

### 3.5 四张表都不改

`conv_session` / `conv_message` / `conv_run` / `conv_message_citation` 的字段全部够用，
`schema.sql` 不动，**不写迁移脚本**（线上库与 schema.sql 已经一致）。

---

## 四、模块边界

### 4.1 依赖方向

```
hify-shared ← hify-ai ← hify-tool ← hify-knowledge ← hify-agent
                                   ↘                  ↙
                            hify-conversation（编排层）
```

- `hify-conversation` 依赖 shared、ai、tool、knowledge、agent
- **不依赖** workflow（两个编排层平行）
- ai / tool / knowledge / agent **不依赖** conversation

### 4.2 `api` 包的公开契约

| 类型 | 作用 |
|---|---|
| `ConversationService` | 会话 CRUD |
| `ConversationChatService` | 流式对话的入口（返回 SSE 写出器） |
| `ConversationSummaryResp` | 会话摘要 |
| `ConversationDetailResp` | 会话详情 |
| `ConversationCreateReq` / `ConversationUpdateReq` | 增改请求 |
| `ConversationListQuery` / `ConversationListResp` | 列表（游标分页） |
| `ConversationMessageResp` | 一条消息（含工具调用与引用） |
| `SendMessageReq` | 发送消息的请求 |
| `ConversationRunResp` | 一轮运行的统计 |

### 4.3 `hify-ai` 新增的公开契约

| 类型 | 作用 |
|---|---|
| `ChatService` | 流式对话 |
| `ChatRequest` | 请求（模型、消息、工具、参数） |
| `ChatMessage` | 一条输入消息（system / user / assistant / tool） |
| `ChatTool` | 一个可调用的工具定义 |
| `ChatToolCall` | 一次工具调用（id、名字、参数 JSON） |
| `ChatListener` | 增量回调 |
| `ChatUsage` | token 用量 |

### 4.4 分层落位

| 层 | 内容 |
|---|---|
| `api` | 上表全部 |
| `internal/entity` | `ConversationDO`、`MessageDO`、`RunDO`、`CitationDO` |
| `internal/mapper` | 四个 Mapper |
| `internal/converter` | `ConversationConverter` |
| `internal/service` | `ConversationServiceImpl`、`ChatOrchestrator`、`ToolLoop`、`ContextBuilder`、`CitationWriter` |
| `internal/controller` | `ConversationController`、`ConversationStreamDispatcher` |
| `internal/config` | `SseWriterConfig`（写出线程池） |

---

## 五、实现要点

### 5.1 协议适配（`hify-ai`）

三个协议各自的请求形状与响应形状：

| | 请求 | 工具定义 | 工具调用 | 工具结果 | 流式格式 |
|---|---|---|---|---|---|
| OpenAI 兼容 | `POST {base}/chat/completions` | `tools:[{type:function,function:{name,description,parameters}}]` | `choices[0].delta.tool_calls[]`（`arguments` 分片） | `{role:"tool",tool_call_id,content}` | SSE，`data: [DONE]` 收尾 |
| Anthropic | `POST {base}/messages` | `tools:[{name,description,input_schema}]` | `content_block_delta` 的 `input_json_delta` | `{role:"user",content:[{type:"tool_result",tool_use_id,content}]}` | SSE，具名事件 |
| Ollama 原生 | `POST {base}/api/chat` | `tools:[{name,description,parameters}]` | `message.tool_calls[]` | `{role:"tool",content}` | **NDJSON**，一行一帧 |

**Ollama 的流是 NDJSON 不是 SSE** —— CLAUDE.md §3.5 早就记了这件事：
「`shared.http.SseFrameReader` 现在只切 SSE 帧，要接 Ollama 得先抽成
「切 SSE 帧」与「按行切 JSON」两种策略」。所以 `LlmHttpClient` 加一个
`streamLines(...)`，按行切 JSON。

**工具调用的参数是分片到达的** —— OpenAI 的 `arguments` 是一段 JSON 字符串的增量，
Anthropic 的 `input_json_delta` 同理。ai 模块内部**先把分片拼完再回调一次完整的工具调用**，
不把分片泄漏给调用方。

### 5.2 编排流程

```
POST /conversations/{id}/messages
  → 校验会话存在且属于当前用户
  → 读 Agent 配置（agent.api 的 assemble）
  → 组装上下文（ContextBuilder）
       · system：Agent 提示词 + 检索到的资料
       · history：最近 20 条消息
       · user：本次输入
  → 建 conv_run（running）
  → 落 conv_message（user）
  → 工具循环（最多 5 轮）：
       ai.ChatService.stream(...) → 收增量文本 → 直接写 SSE
                                 → 收工具调用 → 执行 → 写 SSE → 回灌
  → 落 conv_message（assistant）
  → 更新 conv_run（succeeded / failed）
  → 更新 conv_session（message_count / last_message_at / title）
  → 写 conv_message_citation（引用）
  → SSE done
```

### 5.3 事务边界

- **写库的每个动作单独一个事务**，不把「调上游」包进事务（CLAUDE.md §3.1 E5：
  「事务方法内禁止调用模型、MCP、工具执行等慢能力」）
- 落用户消息、建 run 是两个独立事务
- 落助手消息 + 更新 run + 更新 session + 写引用是一个事务

### 5.4 会话标题

第一条用户消息的前 30 个字符。已有标题不动（用户改过就不覆盖）。

### 5.5 失败处理

- 上游超时 / 报错 → SSE 发 `error` 事件（带错误码与可读消息），run 记 failed
- 工具执行失败 → **不中断整轮**：把失败原因作为工具结果回灌给模型，让模型自己决定怎么办。
  工具失败就中断对话，用户会卡在那儿
- 客户端断开 → 编排线程要能感知（`emitter.onCompletion` / `onError`），停止继续读上游

---

## 六、验证方式

| 阶段 | 验证 |
|---|---|
| 数据模型 | `mvn -pl hify-app -am test -Dtest=AiSchemaMappingTest`；分区定义与线上库逐字比对 |
| Entity + Mapper | `mvn -pl hify-conversation -am test`（映射测试：表名、列、主键、逻辑删除标记） |
| DTO | `mvn -pl hify-conversation -am compile` + 校验注解单测 |
| Service | Mockito mock Mapper，断言状态流转、归属校验、标题生成 |
| 协议适配 | 单测覆盖三套协议的请求体形状与响应解析（含工具调用分片拼接） |
| 编排 | 单测覆盖工具循环（0 次 / 1 次 / 多轮 / 超上限）、知识检索注入、引用落库 |
| Controller | `mvn verify` + `curl` 打真实接口 |
| 前端 | `cd hify-web && npm test && npm run type-check && npm run build` |
| 完整验收 | curl 逐接口 + 浏览器全流程；测试数据删净 |

**验收需要一个真的 OpenAI 兼容模型** —— 写一个最小的本地 mock server
（`/v1/chat/completions`，SSE，能返回文本增量与工具调用），
配一个指向它的接入点，跑通「提问 → 流式输出 → 模型要工具 → 执行工具 → 回灌 → 收尾」全链路。

---

## 七、注意事项

### 依赖与边界

- **conversation 不碰 ai / tool / knowledge / agent 的表**，只走它们的 api 包。
- **ai 不依赖 conversation** —— 反过来成环。

### 线程

- **上游调用与工具执行在 `llmExecutor`**，不占 Tomcat 线程（CLAUDE.md §3.5）
- **写 SSE 在 `sseWriterExecutor`**，不占 llmExecutor（§2.3）
- 线程池饱和时**快速失败**，不做 CallerRuns 回落到编排线程

### 分区表

- **事件流表的实体不继承 `BaseEntity`** —— `@TableLogic` 会给 SQL 追加
  `deleted = 0`，而这三张表没有这一列
- **不写软删** —— 消息是事件流，删了就是删了

### 数据

- **引用存文本快照** —— 知识库重新索引后 chunk id 会变，历史溯源不能依赖 PG 还在
- **不落库验收数据** —— 验收完删净，核对行数

### 验证纪律

- **每层验证通过再进下一层。**

---

## 八、实现期发现

> （实现过程中遇到的实际问题，实时补记。）

### 8.1 `ToolSpec` 里没有 `public_id` —— 工具的跨模块身份用「来源:工具标识」

**症状**：组装工具清单时，工具规格（`ToolSpec`）里没有对外 ID，没法把「Agent 绑的工具」
和「工具规格」对上。

**原因**：`ToolSpec` 是 `ToolProvider` 产出的，provider 只知道 `toolKey`，不知道目录行的
`public_id`（和 agent 模块遇到的是同一件事，见 agent 规格 §8.1）。

**做法**：不在 `ToolSpec` 上加字段（provider 填不出来），改用 **`来源:工具标识`**
（`sourceType:toolKey`）作为工具的跨模块身份 —— 它由 `uk_tool_key (source_type, tool_key)`
保证唯一，而 `(来源, 工具标识)` 这对值正好是执行时 `ToolCatalog.invoke` 要的两个参数。

**副作用**：工具调用消息落库时要多存一个 `toolKey` 字段，回放历史时靠它找回函数名。

### 8.2 模型的函数名必须是 ASCII，且要从「工具标识」派生而不是「显示名」

**症状**：工具名是用户随手起的，可能是中文；而 function calling 协议要求函数名匹配
`^[a-zA-Z0-9_-]{1,64}$`（OpenAI 与 Anthropic 都这么要求）。

**做法**：
- **函数名由「工具标识」派生**，不��显示名 —— 显示名用户随时会改，改了函数名就变了，
  历史消息里的函数名会对不上
- **清洗一次**：非 `[A-Za-z0-9_-]` 的字符折成 `_`，连续的下划线合并，首尾去掉，
  数字开头补 `t_`，长度截到 54（留 10 个字符给去重后缀）
- 全是中文的话清洗后为空 → 退回到 `tool`
- 同名工具加 `_2`、`_3` 后缀去重

**代价**：中文工具名在模型那边是 `tool`、`tool_2` 这种看不出所以然的名字 ——
**但描述里写着它是干什么的**，模型靠描述判断，不靠名字。

### 8.3 工具调用消息存 JSON 正文，出接口时折叠进前一条助手消息

**症状**：工具调用有 id、名字、入参、结果，`conv_message` 里没有对应的列。

**做法**：不��列 —— 表上本来就有 `content_type`（text / markdown / json），
这一列就是给结构化正文准备的。工具消息的正文存一段 JSON：
`{toolCallId, name, toolKey, arguments, result, error}`。

**出接口时再折叠**：工具消息紧跟在发起它的助手消息后面，接口把工具明细挂到那条助手消息的
`toolCalls` 上，工具消息本身不再单独返回 —— 界面上工具调用是助手那一轮的一部分，
不是一条独立的消息。

### 8.4 「每一轮模型调用各留一条助手消息」需要 turn 事件对齐

**症状**：服务端按轮存助手消息，流式输出却是「一次提问一条气泡」——
刷新前后的界面不一致。

**做法**：后端每轮模型调用前发一个 `turn` 事件（`{"index": n}`），前端据此另起一条助手消息。
这样流式看到的分段与刷新后的历史一一对应。

**代价**：SSE 契约多了一个事件类型（前端 `api/chat.ts` 同步加了解析）。

### 8.5 `schema.sql` 里的分区定义本来就在

**症状**：用 `awk '/CREATE TABLE `conv_/,/^\) ENGINE/'` 抽建表语句，抽出来看不到 `PARTITION BY` ——
一度以为 `schema.sql` 漏了分区定义，准备往里补。

**原因**：分区子句写在 `) ENGINE=...` **之后**，awk 的范围匹配到 `) ENGINE` 就停了，
后面的内容根本没抽到。

**做法**：`schema.sql` 一行不改。**教训**：抽 SQL 看结构时别只看匹配到的那一段 ——
分区、索引这些子句可能都在范围之外。

### 8.6 SSE 的错误必须在响应开始之前返回

**症状**：验收发现「向不存在的会话发消息」返回的是 **HTTP 200**，错误藏在 SSE 的 `error` 帧里。

**原因**：SSE 的 HTTP 状态码在**响应头发出那一刻**就定了。controller 直接返回 `SseEmitter`，
后面的编排（校验会话、组装上下文、调模型）全都发生在流已经开始之后 ——
那时候再抛异常，只能变成流里的一帧 `error`，状态码改不了了。

**做法**：**会话归属校验提到 HTTP 层**，在 controller 返回 emitter 之前做完：
```java
SseEmitter emitter = new SseEmitter(...);
dispatcher.stream(id, request, emitter);   // 这里先校验，不存在就抛 → 404
return emitter;
```
校验失败时 emitter 从没被返回给框架，异常正常走全局处理器 → 客户端拿到标准的 404 JSON。

**连带改动**：编排器不再自己查会话，改成接收「已校验的会话」——
校验只做一次，编排器也不用重复查库。
