# Hify

> 一个可本地部署的 AI Agent 开发平台，支持多模型管理、Agent 配置、知识库 RAG、简版工作流、对话交互和 MCP 工具接入，面向团队内部小规模使用。
> 一个人开发，服务 20–50 人内部团队。

---

## 一、项目概述

### 1.1 定位

把核心链路做扎实，而不是做全。业务同事不改代码就能用起来，研发负责高级编排。

### 1.2 功能范围

| 模块 | 分类 | 范围 |
|---|---|---|
| 模型管理 | **核心** | 模型接入点的 CRUD + 连通性测试；统一走 OpenAI 兼容协议 |
| Agent 配置 | **核心** | 选模型、绑工具、设提示词；可选绑知识库、参数区 |
| 对话引擎 | **核心** | 流式输出 + 多轮会话 + 工具调用循环；每个 Agent 一个可分享 Web App |
| 工具集成 | **核心** | 内置工具 / 自定义 HTTP 工具 / MCP 工具，三类共用一个 Tool 接口 |
| 管理控制台 | **核心** | 模型/Agent/知识库/工作流四个列表页 + 日志页 + 设置页，能用即可 |
| 知识库 | 降级 | 只支持 TXT 上传 + 固定分块 + 向量检索 |
| 工作流 | 降级 | 只支持 JSON 配置；节点 6 种：LLM / Agent / 工具 / 知识检索 / If-Else / 输出 |

**明确不做**：可视化拖拽编排、多租户与工作空间、插件市场与插件包、计费与订阅、MCP 反向发布（Hify 应用作为 MCP Server）、第三方可观测集成（Langfuse / LangSmith 等）、标注系统、CLI、应用市场、语音 TTS/STT。

### 1.3 技术栈

- **后端**：Spring Boot 3.x + MyBatis-Plus + MySQL 8.x + Redis 7.x
- **向量**：PostgreSQL 16 + pgvector（独立于 MySQL，双库）
- **前端**：Vue 3 + TypeScript + Element Plus
- **容器化**：Docker + Docker Compose（开发）/ Docker + Kubernetes（生产）
- **MCP**：`io.modelcontextprotocol.sdk:mcp`（Java 17+）
- **LLM 接入**：OpenAI 兼容端点（OpenAI / Ollama / DeepSeek / Qwen / vLLM）+ Anthropic + Gemini，三套适配器

### 1.4 规模与部署形态

- **开发**：Docker Compose。**生产**：Kubernetes，3 副本 `hify-api`（无状态）+ 2 副本 `hify-web`（Vue 静态 + nginx）+ MySQL + PostgreSQL/pgvector + Redis。两者共用同一套镜像与同一套环境变量名。
- 峰值 3–5 QPS、100–150 并发 SSE。**瓶颈在 LLM 长连接，不在并发。固定 3 副本，不做 HPA。**
- JVM `-Xmx512m`，容器内存上限 **1536Mi**（native 部分约 566m：Metaspace ≤128m + DirectBuffer ≤128m + Tomcat 线程栈 200m + 业务线程栈 48m + 其他 ~50m）。`1Gi` 装不下。内存紧张时先降堆到 384m，不压线程数。
- 扩容前提：会话在 MySQL，缓存/限流/幂等/锁全在 Redis，**不用本地 Caffeine 或本地计数**。

---

## 二、架构

### 2.1 模块清单与依赖

**9 个 Maven 模块**：8 个业务模块 + 1 个装配模块 `hify-app`。

| 模块 | 职责 | 允许依赖 | 归属表 |
|---|---|---|---|
| `hify-shared` | 异常体系、错误码、当前用户上下文、trace id；平台域的用户与密钥 | — | `user`、`api_key` |
| `hify-ai` | 模型接入点管理 + 模型调用（chat / embedding / 流式补全） | shared | `ai_model_provider`、`ai_model` |
| `hify-tool` | Tool 抽象 + `ToolProvider` SPI + 内置与 HTTP 两种来源 | shared | `tool_definition` |
| `hify-knowledge` | TXT 上传、固定分块、embedding、向量检索 | shared, ai | `kb_knowledge_base`、`kb_document`（PG: `vec_chunk`） |
| `hify-mcp` | MCP client、server 配置管理、MCP 来源的 ToolProvider | shared, tool | `mcp_server` |
| `hify-agent` | Agent 配置的存取与组装 | shared, ai, tool, knowledge | `ai_agent`（`ai_agent_version` 可选，一期不做） |
| `hify-conversation` | 会话、消息、流式对话编排 | shared, ai, tool, knowledge, agent | `conv_*` |
| `hify-workflow` | 工作流定义的存取与执行（6 种节点） | shared, ai, tool, knowledge, agent | `wf_*` |
| `hify-app` | 启动、装配、静态资源与反代配置；**唯一可执行模块** | 全部 | — |

依赖图形状 —— **箭头指向依赖者**（`A → B` 表示 **B 依赖 A**，与「A → B = A 依赖 B」的直觉相反，务必按此读）：

```
hify-shared → hify-ai / hify-tool / hify-knowledge
hify-tool   → hify-mcp
hify-ai + hify-tool + hify-knowledge → hify-agent
hify-ai + hify-tool + hify-knowledge + hify-agent → hify-conversation / hify-workflow
以上全部                                          → hify-app
```

**四条不变量**（违反即成环，或破坏「拆微服务只改传输层」这个前提）：
- 依赖方向只能从编排层（conversation / workflow）指向能力层（ai / tool / knowledge / agent），**不得反向**
- `hify-ai` 必须是纯能力（配置 CRUD、纯文本/流式补全、embedding）。**任何组合逻辑（知识库注入、工具循环、记忆）都不许出现在 ai**，否则 `ai ↔ knowledge` 成环
- `hify-agent` 只负责「把配置组装成一个可运行的 Agent」，**不执行推理、不调用工具**
- **`hify-tool` 不依赖 `hify-mcp`** —— `tool` 定义 `ToolProvider` SPI，`mcp` 实现它、由 `tool` 收集，方向是 `mcp → tool`。写成 `tool → mcp` 会成环

**一次完整的运行时调用链**（说明数据怎么流）：

```
用户提问
 → conversation：读 Agent 配置（agent.api）
 → conversation：调 knowledge 检索（knowledge.api），拿到 chunk
 → conversation：调 ai 流式补全（ai.api）
 → 模型请求工具 → conversation 调 tool 执行（tool.api，MCP 来源走同一接口）
 → 工具结果回灌模型 → 直到模型不再要工具
 → conversation：写 conv_message + conv_run + conv_message_citation（引用 chunk 时）
```

其中 `ai_agent → ai_model`、`conv_session → ai_agent`、`conv_message_citation → vec_chunk` 都是**逻辑引用**（只存 id，不建外键），理由见 §3.2 与 §3.3。

### 2.2 模块内分层

```
com.hify.<module>/
├── api/          公开契约：interface + 纯 DTO + event（唯一允许被外部 import 的包）
└── internal/     controller / service / mapper / entity / converter / spi / config
```

各层职责与硬约束（完整细则、可执行的正反例与 ArchUnit 检查代码见 `docs/superpowers/specs/2026-10-29-hify-code-organization.md`）：

| 层 | 职责 | 硬约束 |
|---|---|---|
| `api` | 对外契约 | 只放 interface + 纯 DTO + event；**禁止** Spring 注解、ORM 注解、`internal` 引用、继承 `IService` |
| `controller` | HTTP 接入 | 方法体 3–8 行，只做参数绑定、调 service、返回 DTO；**禁止**业务逻辑、`@Transactional`、直接注入 Mapper、返回 DO |
| `service` | 业务逻辑与事务 | `@Transactional` 只出现在这一层；编排其他模块的 `api`；发布事件 |
| `mapper` | 数据访问 | 只放 Mapper 与自定义 SQL；**禁止**业务逻辑；SQL 里的表名必须以本模块表前缀开头 |
| `entity` | 数据库映射 | 只在 `internal` 内使用；**禁止**出现在 `api` 包、作为 controller 返回值、作为跨模块方法参数 |
| `converter` | DO ↔ DTO | 转换逻辑不得堆在 service 里 |
| `spi` | 扩展点接口 | **放在消费方的 `api` 包**（见 §3.2 依赖倒置） |

**`api` 包的未来形态**：现在 `api` 是同一个 artifact 内的一个包。将来某个模块拆成独立服务时，`api` 包会**原样复制**过去，成为 RPC 契约的来源——所以它的接口签名必须「远程调用友好」（§3.2）。**这是「模块内部不用动、只改传输层」这个承诺的兑现前提，改接口签名前先想清楚这一点。**

### 2.3 部署拓扑

```
浏览器
  │ HTTPS
  ▼
Ingress (nginx-ingress) ── TLS 终止 · 路由 · SSE 超时与缓冲策略
  │
  ├── /api/v1/** ──────→ hify-api (ClusterIP, 3 副本)
  │                       ├──→ MySQL             (业务数据)
  │                       ├──→ Redis             (缓存/限流/锁/幂等)
  │                       ├──→ PostgreSQL+pgvector (向量)
  │                       ├──→ MCP server        (独立 Deployment, HTTP transport)
  │                       └──→ LLM API           (出集群: OpenAI / Claude / Gemini)
  │                            本地 Ollama 走 ClusterIP，不出集群
  │
  └── / ────────────────→ hify-web (nginx + Vue 静态, 2 副本)
```

**两种路由方案，二选一，不要混着配：**

- **方案 A（默认，图上画的）**：Ingress 直接把 `/api` 路由到 `hify-api`——SSE 少一跳 nginx，少一层配置、少一个出错点；代价是需要处理跨域（api 侧配 CORS）
- **方案 B**：hify-web 内部的 nginx 反代 `/api` → `hify-api`——同源免 CORS；代价是 SSE 多一跳，**内层 nginx 必须同样配 `proxy_buffering off` + `proxy_read_timeout 3600`**

**不要「外层配了内层没配」**：内层缓冲会让流式变成「等很久后一次性到达」，而且很难被误判成模型慢。

**开发环境（Docker Compose）跑同一组服务**：mysql、postgres(pgvector)、redis、api、web。生产多出来的只是 K8s 的 Service/DNS 与 Ingress，配置靠同一套环境变量名保持一致。

MCP server 独立部署的原因：stdio 传输下 MCP server 是 Hify 在本机 spawn 的子进程，K8s 多副本会导致每个 Pod 各起一份、状态互不一致。

### 2.4 数据模型

**MySQL 15 张（业务库）+ PostgreSQL 1 张（向量库）**；若一期不做发布快照表（`ai_agent_version`），MySQL 为 14 张。ER 图见 `docs/diagrams/hify-data-model.svg`。

| 模块 | 表 |
|---|---|
| hify-shared | `user`、`api_key` |
| hify-ai | `ai_model_provider`、`ai_model` |
| hify-agent | `ai_agent`（`ai_agent_version` 可选，一期不做） |
| hify-tool | `tool_definition` |
| hify-mcp | `mcp_server` |
| hify-knowledge | `kb_knowledge_base`、`kb_document` |
| hify-conversation | `conv_session`、`conv_message`、`conv_run`、`conv_message_citation` |
| hify-workflow | `wf_definition`、`wf_run` |

**PostgreSQL**：`vec_chunk`（分段正文 + embedding + HNSW 索引）

**双库的数据分布**（双库架构最易误解的地方）：知识库的**文档元数据在 MySQL**（`kb_document` 承载状态机），**分段正文与向量在 PostgreSQL**（`vec_chunk`）。两者靠 `kb_id` / `doc_id` 逻辑关联，跨库无外键。

**不落库的**：内置工具（代码注册）、MCP 工具（`tools/list` 动态发现，缓存在应用层）、知识库分段正文（只在 `vec_chunk`）。

**关系**（❌ 表示不建外键，只存 id）

| 父 | 子 | 关系 | FK |
|---|---|---|:---:|
| `user` | `api_key` / `conv_session` | 1 : N | ✅ |
| `api_key` | `conv_session` | 1 : N（记录 API 调用来源） | ❌ 跨模块 |
| `ai_model_provider` | `ai_model` | 1 : N | ✅ |
| `ai_model` | `ai_agent` | 1 : N | ❌ 跨模块 |
| `ai_agent` | `tool_definition` / `kb_knowledge_base` | N : M | ❌ JSON 数组 |
| `kb_knowledge_base` | `kb_document` | 1 : N | ✅ |
| `ai_agent` | `conv_session` | 1 : N | ❌ 跨模块 |
| `conv_session` | `conv_message` / `conv_run` | 1 : N | ✅ |
| `conv_message` | `conv_message_citation` | 1 : N | ✅ |
| `vec_chunk` | `conv_message_citation` | 1 : N | ❌ 跨库 |
| `kb_document` | `vec_chunk` | 1 : N | ❌ 跨库 |
| `wf_definition` | `wf_run` | 1 : N | ✅ |

### 2.5 前端结构

Vue 3 + TypeScript + Vite + Element Plus + Pinia。

```
src/
├── api/          后端接口封装（Axios 实例 + SSE 客户端）
├── views/        页面：模型管理 / Agent 配置 / 知识库 / 工作流 / 日志 / 对话
├── components/   业务组件
├── stores/       Pinia：会话状态、连接状态、当前用户
└── router/       路由与守卫
```

- **管理后台**（模型 / Agent / 知识库 / 工作流 / 日志）用 Element Plus 的表格与表单即可，不额外引入 UI 框架
- **对话页是唯一的自定义界面**：消息气泡 + 流式增量渲染 + 工具调用中间态
- **SSE 客户端封在 `api/` 下统一提供** —— 原生 `EventSource` 只支持 GET 且不能带 header/body，而对话接口需要 POST + body + token，所以必须用 `fetch` + `ReadableStream` 手动解析 SSE 帧
- **连接状态归 Pinia 管**：每个对话页一条 SSE 连接，页面卸载必须 `abort()`，否则连接泄漏
- 静态资源由 nginx 托管并带 hash 长缓存；浏览器侧始终同源（`/api` 与 `/` 都走 Ingress）

### 2.6 构建

```bash
mvn clean verify                              # 全量构建 + 依赖图检查
mvn -pl hify-app -am spring-boot:run         # 本地起应用（需先起 MySQL / PG / Redis）
mvn -pl hify-knowledge -am test               # 只测某个模块
```

- 父 pom `com.hify:hify-parent` 统一管理全部依赖版本（导入 `spring-boot-dependencies` BOM + 内部模块 + 第三方），**子模块 pom 引用内部模块时不写 `<version>`**
- `hify-app` 是唯一可执行模块（`spring-boot-maven-plugin:repackage`，启动类 `com.hify.app.HifyApplication`）
- **依赖环检测**：模块级的环由 Maven 在构建 reactor 时直接报错；artifact 级的环由 enforcer 的 `banCircularDependencies` 在 `verify` 阶段覆盖（该规则需显式引入 `org.codehaus.mojo:extra-enforcer-rules`）

---

## 三、编码规范

**写代码前先读 §3，以及对应的详细规范文档。**

### 3.1 Java 通用规范（21 条）

基于《阿里巴巴 Java 开发手册》提炼。完整版（含更多示例）见 `docs/java-coding-standards.md`。

**命名**
- **N1** 类 `UpperCamelCase`、方法/变量/参数 `lowerCamelCase`、常量 `全大写_下划线`、包名小写单数
  ❌ `class orderService` / `static final int maxRetry`
- **N2** POJO 布尔字段**不加 `is` 前缀** —— Lombok 生成 `isDeleted()`，Jackson 序列化出的是 `deleted` 而非 `isDeleted`，与数据库字段和前端约定都会错位
  ❌ `private boolean isDeleted;`
- **N3** 杜绝 `AbsClass` / `func` / `msg` / `temp` 这类望文不知义的缩写；确需缩写时**必须在注释给出全称**
- **N4** **禁止魔法值**，状态码/业务类型/阈值/超时时间一律抽常量
  ❌ `if (order.getStatus() == 1)`
- **N5** 按角色加后缀：DO / DTO / BO / VO；抽象类 `Abstract`/`Base` 前缀；异常类 `Exception` 后缀；测试类 `Test` 后缀；枚举类 `Enum` 后缀且成员全大写

**异常处理**
- **E1** 异常不用于流程控制，能用 `if` 判断的不要 `throw`
- **E2** 禁止静默吞异常；重抛**必须传 `cause`**，不能只传 `getMessage()`；稳定代码 catch 住并记录，非稳定代码不 catch 往外抛
  ❌ `catch (Exception e) { }` / `throw new RuntimeException(e.getMessage())`
- **E3** 资源必须用 try-with-resources 关闭，禁止在 `finally` 里手写 `close()` 而不判空
- **E4** 自定义异常继承 `RuntimeException`，必须有 `serialVersionUID` + 错误码 + 两组构造器；业务失败靠**错误码**区分不靠 message 文本
  ❌ `class BizException extends Exception`（检查异常，调用方被迫 catch）
- **E5** `@Transactional` 默认**只回滚 `RuntimeException`**（需 `rollbackFor = Exception.class`）；**事务方法内禁止调用模型、MCP、工具执行等慢能力**——它们会长时间持有数据库连接
- **E6** **异常处理必须使用 `ErrorCode` 枚举，禁止硬编码错误码**——错误码字面量和 HTTP 状态码都不许写死。业务失败一律 `throw new BizException(ErrorCode.XXX, ...)`，响应一律 `Result.fail(ErrorCode.XXX, ...)`，HTTP 状态码取 `errorCode.getHttpStatus()`；需要新增错误场景时在 `ErrorCode` 里加枚举常量，不要在调用点造数字
  ❌ `throw new BizException(40001, "参数错误")` / `Result.fail(400, "参数错误")` / `ResponseEntity.status(400)`
  **（`Result` 刻意不提供 `fail(int, String)` 重载，错误码字面量在编译期就写不进去）**

**日志**
- **L1** 必须用 `{}` 占位符，禁止字符串拼接（拼接无视日志级别仍会执行）
  ❌ `log.info("Agent [" + id + "]")`
- **L2** 异常作为最后一个参数传入以打印完整堆栈
  ❌ `log.error(e.getMessage())` / `log.error(e + "")`
- **L3** 禁止 `System.out` / `System.err` / `e.printStackTrace()`
- **L4** 敏感信息（密码、API Key、token、身份证、完整手机号）不入日志；**用户输入引发的异常只在服务端记录，不回显给客户端**
- **L5** 不要用 `if (log.isDebugEnabled())` 包日志

**并发**
- **C1** **禁止用 `Executors` 创建线程池** —— `newFixedThreadPool` 队列无界会 OOM，`newCachedThreadPool` 最大线程数无界
- **C2** 线程池必须用 `ThreadPoolExecutor` 显式传全 7 个参数：队列**有界**、线程工厂**带业务名**、拒绝策略明确。核心线程数：CPU 密集 ≈ N+1，IO 密集 ≈ 2N（LLM/MCP 调用属 IO 密集）
  ```java
  new ThreadPoolExecutor(8, 32, 60L, TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(256),
          new NamedThreadFactory("hify-llm"),
          new ThreadPoolExecutor.CallerRunsPolicy());
  ```
- **C3** `SimpleDateFormat` 非线程安全，用不可变的 `DateTimeFormatter` 或 `java.time`
- **C4** `volatile` **不保证原子性**；`count++` 必须用 `AtomicInteger`/`LongAdder` 或加锁；锁在 `finally` 释放
- **C5** 禁止 `new Thread()`；ThreadLocal（MDC traceId、当前用户）在线程池里必须在 `finally` 里 `remove()`，否则线程复用会串上下文

### 3.2 分层与跨模块

- 跨模块**只能**通过对方的**公开契约**调用——`api` 包（interface + DTO），以及 `hify-shared` 的 `web`（统一响应体 `Result` / `PageResult`、`GlobalExceptionHandler`）与 `exception`（`ErrorCode` / `BizException`）两个包，所有模块都要引用；**禁止**跨模块调用 Mapper、直接查对方的表、或 import 对方的 `internal`
- **跨模块只传 DTO，不传 MyBatis-Plus 实体**。DTO 不带 ORM 注解、无循环引用、字段可 JSON 序列化
- `api` 接口签名必须「远程调用友好」：参数与返回值全是 DTO、无重载、参数 ≤ 3 个、无 checked exception、无业务 default 方法。**判据：能否被 Feign / Dubbo 直接实现**
- `@Transactional` 只出现在 `internal/service` 层
- 扩展点用**依赖倒置**：SPI 接口放消费方。`tool` 定义 `ToolProvider`，`mcp`/`http`/`builtin` 各自实现，`tool` 注入 `List<ToolProvider>` 收集
- 跨模块副作用走 Spring 事件（`ApplicationEventPublisher`），事件流必须单向（发布者层次 ≥ 监听者层次）
- 当前用户、trace id 等上下文**不得依赖 ThreadLocal 跨模块传递**，跨模块接口要显式携带
- 认证必须无状态（JWT）或 Redis 共享 session，不得依赖单机内存 session
- 表前缀：`ai_` `kb_` `wf_` `conv_` `mcp_` `tool_` `vec_`；不建跨模块外键
- 用 ArchUnit 把这些边界变成 CI 里会失败的断言：禁止 import 其他模块的 `internal` 包、能力层不得反向依赖编排层、编排层不得直接访问 Mapper

### 3.3 数据库

**通用**：引擎 InnoDB；字符集 `utf8mb4` / `utf8mb4_0900_ai_ci`；主键 `BIGINT UNSIGNED AUTO_INCREMENT`；对外 ID `CHAR(26)` ULID（`public_id` + `uk_public_id`，只给对外暴露路径的表）；时间 `DATETIME(3)` 存 UTC（**不用 `TIMESTAMP`**）；金额 `DECIMAL(18,4)`；配置用 MySQL 8 原生 `JSON`；**禁止 `ENUM`**；**禁止外键级联删除**；**禁止 `SELECT *`**；所有字段 NOT NULL 优先；单表二级索引 ≤ 5 个；联合索引总长 ≤ 3072 字节。

**分区**：
- **只有事件流表分区**（`conv_message` / `conv_run` / `conv_message_citation` / `wf_run`），**从建表起就分区**，按月 + 一个 `pmax` 兜底分区
- `conv_session` 与所有配置表**不分区** —— 分区列必须在每个 UNIQUE 键中，分区表不能有独立的 `uk_public_id`，而 `conv_session` 需要 public_id 全局唯一（分享链接）
- **MySQL 分区表不支持外键**（既不能定义也不能被引用）→ 事件流表全部改逻辑引用
- 连带：分区表主键必须是 `(id, created_at)`

**多对多**：`ai_agent.tool_ids` / `knowledge_ids` 用 JSON 数组，反向查询用 `JSON_CONTAINS`，**必须建 MySQL 8 多值索引**，否则是全表扫：
```sql
ADD INDEX `idx_tool_ids` ((CAST(`tool_ids`->'$[*]' AS CHAR(64) ARRAY)))
```

**分页**：**禁止 `OFFSET` 深分页**，一律用游标分页——排序键恒为 `(created_at, id)` 复合并用行值比较 `(a,b) < (x,y)`。总数用 `LIMIT N+1` 判断而非 `COUNT(*)`。单页上限 100（管理台 200），不做页码跳转。

**向量**：pgvector HNSW 索引（`<=>` 配 `vector_cosine_ops`），`m`/`ef_construction` 建索引时定、改动需重建；`vec_chunk` 必须有 `UNIQUE(doc_id, chunk_index)` 保证 upsert 幂等；PG 侧一律用 `NamedParameterJdbcTemplate` 手写 SQL，**不用 MP 的 `BaseMapper`**。

**跨库一致性**：两库之间没有分布式事务。知识库写入必须**可重入**（PG 侧 upsert 幂等 + MySQL 侧状态机 UPLOADED/INDEXING/READY/FAILED/DELETING + 定时任务兜底重跑）。**删除顺序固定为「MySQL 软删 → 异步删 PG → MySQL 硬删」** —— 反过来会在中途崩溃时导致检索静默返回空。`conv_message_citation` 同时存 `chunk_id`、`doc_id` + `chunk_index`、**引用文本快照**，保证知识库重新索引后历史溯源仍可读。

### 3.4 流式（SSE）

- **用 Spring MVC，不引入 WebFlux** —— MyBatis-Plus 是 JDBC 阻塞，WebFlux 下要用 R2DBC 而 MP 的 R2DBC 支持不成熟
- 返回 `Flux<ServerSentEvent>`，**必须 `.publishOn(专用调度器)` 把 servlet 写出移出 Reactor 事件循环**
- **`boundedElastic` 必须显式配置**：默认是 `10 × availableProcessors()`，容器 `limits.cpu: 2000m` → 只有 20 线程，而这正是 SSE 写出池；一个慢客户端的阻塞写就占住一个，20 个慢客户端能让全部 SSE 停摆而 CPU 指标正常
  ```java
  Scheduler SSE_WRITE = Schedulers.newBoundedElastic(48, 1000, 60, TimeUnit.SECONDS);
  ```
  **不要靠调高 CPU limit 来解决线程池问题** —— CPU limit 是资源决策，线程池是正确性决策
- **必须显式设长超时**：Tomcat `asyncTimeout` 默认 30s，会掐断 60s 的生成。`spring.mvc.async.request-timeout: 30m` 或 `new SseEmitter(180_000L)`
- **必须发心跳**（15–30s）：保活、检测死客户端、防止中间代理掐断空闲连接
- `ServerSentEvent.data()` **传对象让 Jackson 序列化**，不要传裸 String（含换行会破坏 SSE 分帧）
- `SseEmitter` 不是线程安全的，不能并发 `send()`；多来源合并用 `Sinks.Many`
- **前端不能用原生 `EventSource`**（只支持 GET、不能带 header 和 body）→ `fetch` + `ReadableStream` 手动解析 SSE 帧
- K8s：Ingress 与内层 nginx 都要 `proxy_buffering off` + `proxy_read_timeout 3600`；面向客户端要开 HTTP/2（否则浏览器 6 连接上限会让多标签页 SSE 互相阻塞）；优雅停机 `terminationGracePeriodSeconds: 180` + `preStop: sleep 10` + `maxUnavailable: 0`；**liveness 只含 `livenessState`**，readiness 才含 db/redis

### 3.5 LLM 调用

- **统一抽象**：内部只认 `LlmClient{ provider, model, stream(), complete() }` 与 `LlmChunk{ text, toolCall, usage, finishReason }`；各 provider 的 SSE 帧格式差异（OpenAI 的 `data:[DONE]`、Claude 的 `content_block_delta`、Gemini 的 `candidates[]`）在适配器里归一化，引擎不感知
- **用 WebClient（响应式），不用 RestTemplate（阻塞）** —— 阻塞会占住 Tomcat 线程
- **每 provider 一个连接池**，`maxConnections` 按并发模型算（云端 48/副本，Ollama 6 因受显存限制），`pendingAcquireTimeout: 5s`，队列有界
- **每用户每 provider 在途上限 3**（Redis 原子计数），防单用户多标签页吃光整个池
- **超时四层**：连接 5s（Ollama 2s）、首字节/chunk 空闲 30s（Ollama 90s，冷启动要加载模型）、整体 120s（Ollama 600s）。流式用 `Flux.timeout()` 表达 chunk 空闲超时，不依赖 Reactor Netty `responseTimeout` 的版本语义
- **重试**：只重试连接失败/超时/429/5xx/529；**指数退避 + full jitter**（`random(0, cap)`，无抖动会在故障期间惊群）；429 优先用服务端 `retry-after`；**流式只在首个 chunk 之前重试**（已输出内容后重试会把重复内容拼进用户的流）；**永不重试工具执行**（有副作用）
- **熔断按 provider 独立**，且 `ignoreExceptions` 排除 429/鉴权/参数错误（429 是"你太频繁"不是"我坏了"，算进失败率会误开熔断）
- **降级链是模型接入点配置的一部分**，不在 Agent 里（Agent 的提示词是针对某模型调过的）。**已输出内容后不能再降级**；降级必须对用户可见（响应带 provider 标识）
- **上下文必须有界**：`max-context-tokens: 16384`、`max-output-tokens: 4096`、`spring.codec.max-in-memory-size: 2MB`（WebClient 默认 256KB，非流式响应稍大即抛异常）。加内存解决不了上下文无界
- **MCP**：官方 Java SDK 但仅 Tier 2，**第 1 周必须做 spike**（stdio + Streamable HTTP 双传输、`tools/list` 分页、重连语义）。SDK 默认 HTTP 传输是阻塞的 JDK HttpClient，MCP 调用必须 `subscribeOn(boundedElastic)` 隔离到事件循环之外，否则一次慢调用冻结所有 SSE。stdio 模式每个 Pod 各 spawn 一个进程、副本间状态不一致 → **MCP server 应独立部署用 HTTP transport**；server 配置仅管理员可见可改

---

## 四、关键决策

> 每条只说「决定了什么 / 为什么 / 替代方案是什么」。具体规则见 §3。

### 产品范围
- **功能范围按「核心 / 降级 / 不做」三分** —— 核心 = 缺了产品就不成立；降级 = 缺了能用但价值大打折扣；不做 = 缺了不影响核心链路。范围按此划线。
- **每个模块只做最小可用，不做满** —— 一人开发，广度优先于深度。
- **砍掉可视化拖拽** —— 画布是 Dify 前端成本的大头（画布 1619 文件 vs 引擎 95 文件），一人做要 3–4 周而核心链路不需要。**替代方案：业务同事走 Agent 配置表单（零代码），研发走工作流 JSON**，两条路径覆盖「业务为主、研发兜底」的混合人群。
- **只做一种流程形态（Chatflow 语义）** —— 双形态意味着引擎、变量、节点（Answer vs Output）、发布方式各写两遍。批处理需求走 HTTP API + 文件上传。
- **代码执行作为工具层的内置工具，不做独立节点类型** —— 沙箱边界只需维护一处。
- **砍掉插件体系** —— Dify 的插件体系是 584 文件的独立子系统（包管理、沙箱、审核、签名），对内部场景是纯负担。**替代方案：内置工具 + 自定义 HTTP 工具 + MCP 工具**，MCP 是行业标准且不依赖 Dify 生态。

### 架构与代码组织
- **Maven 多模块按功能拆** —— 编译期强制边界，且「将来拆微服务只需改传输层」这个承诺需要它成立。替代方案是单模块 + 包隔离（更轻，但边界靠自觉，且将来拆分要重做构建结构）。
- **`api`/`internal` 双层 + 跨模块只传 DTO** —— 这是「拆微服务不返工」的前提，事后补要改所有接口签名。代价是多写一层包。
- **扩展点用依赖倒置** —— `tool` 既是工具提供者又是来源的消费者，让 `tool` 依赖 `mcp` 会成环。
- **跨模块事件不引入 MQ** —— 四个事件都是低频的「进程内缓存失效」信号，拆成独立服务时改用 Redis + TTL 刷新即可。**只有出现真正的跨进程业务契约时才引 MQ。**

### 技术选型
- **Spring Boot + MyBatis-Plus + Vue3/Element Plus** —— 工程化成熟度让一个人能快速交付完整系统。代价：相比 FastAPI 多写约 2–4 天基础设施代码（统一异常体系、事务边界规范、Security 自拼），换来 CRUD 效率与管理后台开发速度。
- **双库：MySQL（业务）+ PostgreSQL/pgvector（向量）** —— MySQL 是团队既有技术栈；向量库独立成 Service 后，将来换 Qdrant/Milvus 只需替换 `knowledge` 模块的数据访问实现。**已否决：整体换 PostgreSQL 单库**（少一个组件，但放弃既有栈）。
- **不引入 WebFlux** —— MyBatis-Plus 是 JDBC 阻塞，WebFlux 下要用 R2DBC 而 MP 的 R2DBC 支持不成熟。**替代方案：WebFlux + R2DBC 或 Spring Data R2DBC**，代价是重做数据层。
- **LLM 适配器只做 3 个** —— Ollama 有 OpenAI 兼容端点（0.1.x+），不必单写适配器。
- **模型接入点与模型分两张表** —— **连通性测试的粒度决定表的粒度**：端点能通不代表某个模型名在该账号下可用，一张表表达不了这两件事。

### 数据模型
- **明确哪些东西不落库** —— 内置工具代码注册、MCP 工具动态发现、分段正文只在 PG。避免把易变数据塞进数据库。
- **主键与对外 ID 分离** —— 随机 UUID 做聚簇主键会造成 B+tree 碎片与 buffer pool 命中率下降；ULID 有序、紧凑、不可枚举（分享链接防遍历猜 id）。
- **多对多用 JSON 数组不建关联表** —— 反向引用不落库，工具被删除时不会有孤儿关联行。代价是无法 JOIN、无法保证引用有效性，靠多值索引兜住性能。
- **引用关系存文本快照** —— 知识库重新索引后 chunk id 会变，历史对话的溯源不能依赖 PG 还在。
- **用户与 API Key 归 `hify-shared`** —— 只有 2 张表，单开 Maven 模块不划算；用户域长大后再独立成模块，届时是纯搬迁。
- **API Key 是用户级而非应用级** —— 用户级保留调用者身份做审计；应用级会让「谁调的」退化成「哪个共享 key 调的」。

### 部署与容量
- **开发用 Compose、生产用 K8s** —— 开发用 K8s 启动慢且难调试；生产用 K8s 正好兑现「为扩容留口子」。
- **固定 3 副本不做 HPA** —— SSE 是 I/O 密集，占满连接数时 CPU 可能只有 20%，按 CPU 扩容永不触发；按并发数扩容需要 prometheus-adapter 这套额外部署。
- **Tomcat 线程保持默认 200，不调低** —— 正常路径 SSE 不占 Tomcat 线程，但它是最后一道缓冲：一旦某处漏了 offload，200 的余量能让故障以「变慢」而非「全面拒绝服务」的形式暴露。内存紧张时先降堆。

---

## 五、待确认

- `ai_agent` 与 `wf_definition` 是否记 `created_by`（→ `user.id`）。倾向要——管理台需要「我创建的」视图，也能区分「删了会不会有人用」
- 认证方式：共享密码（则 `user` 表不存密码）／ OIDC ／ LDAP。决定 `user` 表字段与登录接口
- `ai_agent_version`（发布快照）要不要 —— 倾向一期不要，Agent 配置保存即生效；真需要时是纯新增表
