# hify-mcp 模块设计规格

> 阶段 0（咨询模式）的产出物。日期：2026-11-01 · 状态：已确认，直接进入实施
>
> **编号约定**：本文里 `§1.2` / `§2.1` / `§3.5` 指 **CLAUDE.md** 的章节；
> `§L6` / `§M6` 指 `docs/superpowers/specs/2026-10-29-hify-code-organization.md` 的条目；
> `§一` / `§二` 指本文自身的章节。

---

## 一、需求

### 1.1 模块定位

按 CLAUDE.md §2.1，`hify-mcp` 负责 **MCP client、server 配置管理、MCP 来源的 ToolProvider**，归属表 `mcp_server`。依赖 `shared` 与 `tool`。

CLAUDE.md §3.5 定的边界：

- **MCP server 应独立部署用 HTTP transport** —— stdio 模式下每个 Pod 都 spawn 一个进程，副本间状态不一致
- **server 配置仅管理员可见可改**
- **SDK 默认 HTTP 传输是阻塞的 JDK HttpClient，调用必须与 SSE 流隔离**

一期交付：

| # | 交付物 | 说明 |
|---|---|---|
| 1 | Server 配置 CRUD | `mcp_server`：名称、传输方式、stdio 的命令/参数/环境变量、http 的地址/请求头、启停 |
| 2 | 连接测试 | 连上去 → initialize → `tools/list`，报告 server 信息与工具数 |
| 3 | 工具发现 | `tools/list` 的结果写进 `mcp_server.tool_cache`，并登记进 `tool_definition` |
| 4 | `McpToolProvider` | 实现 `tool` 模块的 `ToolProvider` SPI，让 MCP 工具进统一目录、可被 Agent 绑定 |
| 5 | 兜底同步 | 定时刷新工具目录，多副本用 Redis 锁去重 |
| 6 | 管理台页面 | MCP server 列表、增删改、连接测试、同步工具 |

### 1.2 明确不做

- MCP server 的**发布**（Hify 作为 MCP Server 对外暴露工具）—— CLAUDE.md §1.2 明确「MCP 反向发布」不做
- MCP 的 resources / prompts / sampling / elicitation —— 一期只用 tools
- stdio server 的进程池共享（多副本各自 spawn）

---

## 二、选型（决定了什么 / 为什么 / 替代方案）

### 2.1 传输方式 —— **stdio 与 http（Streamable HTTP）都支持**

**决定**：`mcp_server.transport` 取 `stdio` / `http`，两种都用官方 SDK 的传输实现。

**为什么**：CLAUDE.md §3.5 说 MCP server 应独立部署用 HTTP transport，但那是**生产建议** —— 本地开发与内部小工具仍要能起一个 stdio server（`npx -y @modelcontextprotocol/server-filesystem …`）。两种都实现，成本只是多一个分支。

**替代方案**：一期只做 HTTP —— 本地开发起不了 stdio server，模块的可用面窄一半。**已否决。**

**代价**：stdio 模式下每个副本都会 spawn 一个进程，副本间状态不一致 —— 这是 MCP 协议本身的性质，不是实现问题。

### 2.2 客户端生命周期 —— **按 server 缓存长连接 + 空闲回收**

**决定**：维护一个 `McpClientManager`，按 server 的 publicId 缓存 `McpSyncClient`；调用时取用，超过空闲 TTL（默认 5 分钟）就关掉重建。

**为什么**：每次调用都新建客户端的话，stdio 传输每次都要 spawn 一个进程（几百毫秒起），工具调用的延迟不可接受；HTTP 传输每次都要重新 initialize。

**替代方案**：每次调用新建 —— 实现最简单，但延迟高。**已否决。**

**代价**：要管好连接的关闭（应用停机时统一关、server 被删时关掉它的连接、TTL 到期时关）。用一个 `ConcurrentHashMap` + `synchronized` 保护即可，不需要连接池。

### 2.3 工具登记 —— **复用 tool 模块的统一目录**

**决定**：MCP 工具通过 `tool` 模块的 `ToolSyncService` 登记进 `tool_definition`，`source_type='mcp'`。

**为什么**：CLAUDE.md §4 已定「工具目录是统一目录，三种来源都落 `tool_definition`」。MCP 是第三种来源，走同一个同步器，规则（名字撞车跳过、消失下架、幂等 upsert）完全一致。

**关键设计**：`McpToolProvider.listTools()` **实时**调 `tools/list`（而不是只读缓存），并顺手把结果写回 `mcp_server.tool_cache` 供管理台展示。

**为什么实时**：管理台点「同步目录」时要求**立即生效**。如果 provider 只读缓存，MCP 侧刚刷新的缓存要等下一次 tool 同步才会进目录，多了 10 分钟的延迟。实时调用让一条同步路径搞定。

**代价**：每次目录同步都要连一遍所有 MCP server。同步每 10 分钟一次、server 数量是内部规模，可接受；单个 server 超时不影响其它 server（逐个 try/catch）。

### 2.4 凭据 —— **http 的请求头逐值加密**

**决定**：`mcp_server.headers` 里的敏感值用 `shared.crypto.CredentialCipher` 逐值加密（与 tool 模块同一套做法），keyId 用 `mcp-credential-v1`。

**为什么**：MCP server 的请求头常有 `Authorization: Bearer xxx`。JSON TypeHandler 只做转换不加密（Provider 阶段踩过）。

**替代方案**：明文存 —— 不行。

### 2.5 调用隔离 —— **MCP 调用走 `llmExecutor`**

**决定**：MCP 调用是阻塞的（等上游响应），提交到 `llmExecutor`，不占 Tomcat 工作线程。

**为什么**：CLAUDE.md §3.5 明确「SDK 默认 HTTP 传输是阻塞的 JDK HttpClient，调用必须隔离，否则一次慢调用会冻结所有 SSE 流」。

### 2.6 状态 —— **`mcp_server.status` 由真实调用结果更新

**决定**：`status` 取 `unknown` / `online` / `offline`，由连接测试与目录同步的真实调用结果更新；`last_checked_at` 记最近一次探测时间。

**为什么**：这是给管理员看的「这个 MCP server 现在通不通」。

---

## 三、数据模型

### 3.1 `mcp_server` 新增一列

```sql
ALTER TABLE `mcp_server`
  ADD COLUMN `config_version` BIGINT UNSIGNED NOT NULL DEFAULT 1
    COMMENT 'MCP 服务配置版本，由业务层递增，用于乐观锁';
```

**为什么**：server 的地址 / 命令 / 请求头都会被改，两个管理员同时改会互相覆盖。与 `ai_model_provider` / `ai_model` / `kb_knowledge_base` 的做法一致。

### 3.2 不动的部分

`mcp_server` 现有的 `transport` / `command` / `args` / `env` / `url` / `headers` / `enabled` / `status` / `last_checked_at` / `tool_cache` / `tool_cache_at` 全部保留。`uk_name` 覆盖软删行 → 服务层校验时报错说清楚。

### 3.3 `tool_definition` 不动

MCP 工具走 tool 模块已有的 `source_type='mcp'` 分支，`config` 里记 `mcpServerPublicId` 与上游的 tool name。

---

## 四、模块边界

### 4.1 依赖方向

```
hify-shared ← hify-tool ← hify-mcp
```

- `hify-mcp` 依赖 `shared` 与 `tool`（实现 `tool` 的 `api` 包里的 `ToolProvider` SPI）。**不依赖 `ai`** —— MCP 工具不需要模型。
- **不依赖** conversation / workflow（编排层）。
- `hify-tool` **不依赖** `hify-mcp` —— 方向是 `mcp → tool`，反过来会成环。

### 4.2 `api` 包的公开契约

| 类型 | 作用 |
|---|---|
| `McpServerService` | server 配置 CRUD、连接测试、工具同步 |
| `McpServerSummaryResp` | server 摘要（不含请求头密文） |
| `McpServerCreateReq` / `McpServerUpdateReq` | 增改请求 |
| `McpServerListQuery` / `McpServerListResp` | 列表（游标分页） |
| `McpConnectionTestResp` | 连接测试结果：server 信息、工具数、工具名 |
| `McpToolSyncResp` | 工具同步统计 |

### 4.3 分层落位

| 层 | 内容 |
|---|---|
| `api` | 上表全部 |
| `internal/entity` | `McpServerDO`（继承 `BaseEntity`） |
| `internal/mapper` | `McpServerMapper` |
| `internal/converter` | `McpConverter` |
| `internal/client` | `McpClientManager`（客户端缓存）、`McpTransportFactory`（按传输方式建客户端） |
| `internal/service` | `McpServerServiceImpl`、`McpToolSyncService` |
| `internal/spi` | `McpToolProvider`（实现 `tool` 的 `ToolProvider`） |
| `internal/controller` | `McpServerController` |

### 4.4 传输与客户端

```java
// stdio
var parameters = ServerParameters.builder(command).args(args).env(env).build();
var transport = new StdioClientTransport(parameters, McpJsonDefaults.getMapper());
var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build();

// http（Streamable HTTP）
var transport = HttpClientStreamableHttpTransport.builder(url)
        .httpRequestCustomizer(request -> headers.forEach(request::header))
        .connectTimeout(Duration.ofSeconds(5))
        .build();
var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build();
client.initialize();
```

---

## 五、实现要点

### 5.1 工具同步

```
McpToolProvider.listTools()
  → 对每个 enabled 的 mcp_server：
       建/取客户端 → initialize → listTools(分页跟 nextCursor)
       → 结果写回 mcp_server.tool_cache + tool_cache_at + status
  → 汇总成 List<ToolSpec>（toolKey = 上游工具名，config 里记 mcpServerPublicId）
  → tool 模块的 ToolSyncService 统一登记进 tool_definition
```

**单个 server 失败不影响其它**：逐个 try/catch，失败的跳过并记 warn。

### 5.2 `tools/list` 分页

`ListToolsResult.nextCursor()` 非空就继续拉，直到为空。**一期最多翻 20 页**，防止上游异常导致无限翻页。

### 5.3 工具调用

```
McpToolProvider.invoke(ctx, toolKey, args)
  → 按 toolKey 找到所属 server（tool_definition.config.mcpServerPublicId）
  → 取客户端 → callTool(CallToolRequest(name=toolKey, arguments=args))
  → content 里的 TextContent 拼成字符串返回给模型
```

`isError=true` 的结果转成 `ToolResult.failure(...)`。

### 5.4 兜底同步

每 10 分钟跑一次目录同步（复用 tool 模块的定时任务）：MCP provider 的 `listTools()` 会被触发 → 顺带刷新 `tool_cache` 与 `status`。多副本用 Redis 分布式锁去重（与其它定时任务同一套）。

---

## 六、验证方式

| 阶段 | 验证 |
|---|---|
| 数据模型 | `mvn -pl hify-app -am test -Dtest=AiSchemaMappingTest`；迁移脚本执行前后核对行数 |
| Entity + Mapper | `mvn -pl hify-mcp -am test`（映射测试：表名、列、主键、JSON TypeHandler、逻辑删除） |
| DTO | `mvn -pl hify-mcp -am compile` + 校验注解单测 |
| Service | Mockito mock Mapper，断言状态流转与错误码 |
| Provider | `McpToolProvider` 的单测：server 找不到、单 server 失败不影响其它、工具名撞车跳过 |
| Controller | `mvn verify` + `curl` 打真实接口 |
| 前端 | `cd hify-web && npm test && npm run type-check && npm run build` |
| 完整验收 | curl 逐接口 + 浏览器全流程；测试数据删净 |

**验收需要一个真的 MCP server** —— 用 `npx -y @modelcontextprotocol/server-everything` 或本地写一个最小 server，起在本地端口，验证 `tools/list` 与 `tools/call` 的真实链路。

---

## 七、注意事项

### 依赖与边界

- **不要让 `hify-tool` 依赖 `hify-mcp`** —— 方向是 `mcp → tool`，反过来成环。MCP 的工具靠实现 `tool` 的 `ToolProvider` SPI 接进来。
- **MCP 不依赖 `hify-ai`** —— MCP 工具不需要模型。

### 阻塞与线程

- **MCP 调用是阻塞的**（SDK 默认 HTTP 传输是阻塞的 JDK HttpClient），必须走 `llmExecutor`，不占 Tomcat 线程，更不能占 SSE 的写出线程。
- **stdio 传输会 spawn 进程** —— 进程不共享给其它副本；应用停机时要关掉客户端，否则子进程会成孤儿。

### 目录同步

- **工具登记走 tool 模块的统一同步器** —— 不要在 mcp 模块里自己往 `tool_definition` 写，那会绕过「名字撞车跳过、消失下架」这些规则。

### 验证纪律

- **每层验证通过再进下一层。**
- **不要把验收数据留在库里**；删完核对行数。

---

## 八、实现期发现

### 8.1 目录同步的「空列表」有两种含义，必须让 provider 自己说

**症状**：删掉最后一个 MCP 服务后，它的两个工具行仍留在统一目录里、且是启用状态 —— 目录里留着调不通的工具。

**原因**：tool 模块的同步器把「provider 返回空列表」一律当成「这个来源不由 provider 发现，跳过」。但 MCP 源的空列表恰恰意味着「这个来源的工具全部没了」，应该下架而不是跳过。

**改法**：`ToolProvider` 增加一个方法表达这件事。

```java
/** true = 空列表表示「工具全部下架」；false = 空列表表示「不由 provider 发现」。 */
default boolean managesDirectoryRows() { return false; }
```

- `builtin` / `mcp` → true（代码里的内置工具被删、MCP 的服务被删 → 旧行停用）
- `http` → false（HTTP 工具由用户在管理台直接创建，不是同步出来的）

**这条同时修掉了一个潜在的老 bug**：内置工具从代码里删掉时，旧行以前也会一直留着。

### 8.2 凭据加密收敛到 shared，只有一把钥匙

**决定**：`hify-shared` 提供唯一的 `CredentialCipher` 与 `SensitiveHeaderCipher`（keyId `hify-credential-v1`，环境变量 `HIFY_CREDENTIAL_ENCRYPTION_KEY`，回退 `HIFY_TOOL_ENCRYPTION_KEY`），tool 与 mcp 都用它。

**为什么**：原先每个模块各注册一个 `CredentialCipher` bean，容器里出现同类型 bean，注入点要到处加 `@Qualifier`；而且同一份凭据换个模块就解不开。

**代价**：封套的 keyId 变了（`tool-credential-v1` → `hify-credential-v1`），旧密文解不开 —— 换 keyId 必须配迁移。本地开发库里有几条软删的测试数据带着旧密文，不影响。

### 8.3 不要把 SDK 的类型存进数据库

MCP SDK 2.1.0 用的是 **Jackson 3**（`tools.jackson.*`），项目本体用 Jackson 2（`com.fasterxml.jackson.*`）。把 `McpSchema.Tool` 直接序列化进 JSON 列会牵出两套 Jackson 的注解与模块差异。

**做法**：落库的是自己拼的普通 Map（`{name, description, inputSchema}`），读取时用项目自己的 `JsonUtils` 解析 —— 两边都不碰对方的类型。

### 8.4 模板属性值里的 `//` 会让 vue-tsc 报出完全无关的错误

**症状**：`vue-tsc` 报「Cannot find name 'form'」，位置在几行之外的插槽变量上 —— 看起来像泛型推导或作用域问题。

**真因**：`placeholder="例如 http://127.0.0.1:9000/mcp"` 里的 `//` 被 vue-tsc 当成注释，**该元素就没闭合**，后面的兄弟节点跟着错位。

**做法**：网址放脚本常量，模板里 `:placeholder="urlHint"`（CLAUDE.md §3.6 已记这条规则，但没记「报错位置与病因完全无关」这个症状，排查时极易误判）。

### 8.5 MCP 工具标识带服务前缀

不同 MCP 服务可能提供同名工具，`tool_definition` 的 `tool_key` 必须唯一。工具标识取 `<服务 publicId>:<上游工具名>`，前缀同时让 `invoke` 不用反查目录就知道该连哪个服务。

代价是工具标识变长了（26 + 1 + 工具名），但 `tool_key` 是 VARCHAR(128)，够用。
