# hify-tool 模块设计规格

> 阶段 0（咨询模式）的产出物。**本文档未经确认前不建表、不写代码。**
> 日期：2026-10-31 · 状态：待确认

---

## 一、需求

### 1.1 模块定位

按 CLAUDE.md §2.1，`hify-tool` 负责 **Tool 抽象 + `ToolProvider` SPI + 内置与 HTTP 两种来源**，归属表 `tool_definition`。它**不执行编排**（工具执行结果由编排层落库，§M6），也**不认识会话**。

一期交付六块：

| # | 交付物 | 说明 |
|---|---|---|
| 1 | Tool 抽象 | `ToolSpec`（名称 / 描述 / 入参 JSON Schema）、`ToolResult`（成功 / 失败 / 内容）、`ToolInvokeContext`（用户 id、trace id） |
| 2 | `ToolProvider` SPI | 放 `hify-tool/api`，三类来源各自实现，模块注入 `List<ToolProvider>` 收集（§M10） |
| 3 | HTTP 来源 | 用户在管理台创建自定义 HTTP 工具，落 `tool_definition`，可「测试调用」 |
| 4 | 内置来源 | 代码注册一个 `current_time` 工具，验证 SPI 链路真实跑通 |
| 5 | 工具目录 | 跨来源聚合成「哪些工具可以绑给 Agent」，供 `hify-agent` 调用 |
| 6 | 管理台页面 | 工具列表 + HTTP 工具 CRUD + 测试调用 |

MCP 来源**本期不做**，但 SPI 的 `sourceType()` 分发、目录表的 `mcp` 取值、`McpToolListChangedEvent` 全部留好接口位（§M8 已定事件：mcp → tool，进程内缓存失效）。

### 1.2 明确不做

- MCP client / `mcp_server` 的配置管理（`hify-mcp` 模块，本期不动）
- 工具的权限控制、审批流、调用审计
- 内置代码执行工具（沙箱）—— CLAUDE.md 说它归工具层，但风险高，后置
- 工具的分组 / 分类 / 标签

---

## 二、选型（决定了什么 / 为什么 / 替代方案）

### 2.1 `tool_definition` 存哪些来源的行 —— **三种来源全存（统一目录）**

**决定**：`tool_definition` 是**唯一**的可绑定工具目录。内置工具与 MCP 工具也各有一行；HTTP 工具由用户创建。目录表是「有哪些工具可绑给 Agent」的唯一真相来源。

**为什么**：

- `ai_agent.tool_ids` 的既有设计是「工具 **ID** 数组」（JSON + 多值索引）。若内置工具不落库，它就没有 row id，Agent 绑定时就只能存 `builtin:current_time` 这类字符串，`tool_ids` 的语义从「ID 数组」退化成「标识数组」，与并排的 `knowledge_ids` 不一致。
- `uk_tool_key(source_type, tool_key)` 的存在本身就暗示多来源共表 —— 若只有一种来源，`source_type` 就不必进唯一键。
- 统一目录让「工具启停」有地方落：内置工具下架、MCP server 断开导致的工具失效，都是把行置 `enabled=0`，不需要在别处维护第二份状态。

**替代方案**：只存 HTTP 来源（内置走代码注册、MCP 读 `mcp_server.tool_cache`）。好处是不需要同步机制；代价是工具标识不统一、`tool_ids` 语义分裂、MCP 工具的启停状态无处安放。**已否决。**

**代价**：需要同步机制（见 §五.3）。内置工具行是代码的镜像，代码删掉一个内置工具后，表里的行要标 `enabled=0` 而不是留着。

**与 CLAUDE.md 的冲突**：§2.4「不落库的」列了内置工具与 MCP 工具。**本设计推翻该条** —— 「不落库」的原意是「不维护第二份需要人工同步的状态」，而统一目录里的这些行全部由程序同步、用户不手工维护。§2.4 需要在实现阶段同步修改。

### 2.2 HTTP 工具的凭据 —— **抽到 `hify-shared` 统一加密**

**决定**：新增 `com.hify.shared.crypto.CredentialCipher`，封装格式与 `ProviderCredentialCipher` 完全一致（`{version, keyId, nonce, ciphertext, tag}`，AAD = keyId）。`tool` 模块用它；`hify-ai` 的 `ProviderCredentialCipher` **本期不动**。

**为什么**：JSON TypeHandler 只做 JSON 转换不加密（Provider 阶段踩过）。HTTP 工具的请求头里常有 `Authorization: Bearer xxx`，落库必须加密。而 `ProviderCredentialCipher` 在 `hify-ai/internal/security`，`tool` 不能依赖 `ai`（依赖图是单向的，ai → tool 的方向不存在）。

**替代方案**：

- 在 `tool` 里再写一份 cipher —— 拒绝，重复代码。
- 不加密，限制请求头白名单 —— 拒绝，用户接自建 API 会很难用，且凭据仍以明文躺在库里。
- 把 `ProviderCredentialCipher` 一并搬到 shared，tool 与 ai 共用 —— **更干净，但本期不做**：Provider 那边现有密文的 `keyId` 是 `provider-key-v1`，AAD 绑定在 keyId 上，改动有兼容风险。等 shared 版本稳定后再合并。

**keyId 分离**：tool 用 `tool-credential-v1`，Provider 用 `provider-key-v1`。即使共用同一把密钥，两边的密文也不能互换 —— 这是有意的。

**密钥注入**：`${hify.tool.encryption-key:${HIFY_TOOL_ENCRYPTION_KEY:}}`，Base64 编码的 32 字节。缺失或长度不对时**拒绝写入凭据**（fail closed），不降级成明文。

### 2.3 HTTP 工具的入参映射 —— **整体作为 body / query**

**决定**：模型给的 `args`（JSON 对象）整体作为请求体（POST）或整体作为查询参数（GET）。`schema` 只用于告诉模型「该传哪些参数」，不参与执行期的映射。

**为什么**：一期绝大多数自建 API 都是 POST + JSON body，整体映射覆盖了主要场景；实现最简单，没有第二套「schema 注解 → 位置」的映射规则要维护。

**替代方案**：按 JSON Schema 分区到 query / header / body（需要 schema 里带 `in` 标注，且 schema 同时承担「给模型看」和「给执行器看」两种职责）；固定 body 模板 + `{{arg}}` 占位（要自己实现一套模板语言，报错难定位）。**均已否决**，留待二期按真实需求再定。

**代价**：接 GET + query + path 参数的 REST 接口时体验差（path 参数拼不进去）。

### 2.4 一期做哪些来源 —— **HTTP + 一个内置示例**

**决定**：交付 HTTP 来源（用户自建）+ 一个无风险的内置工具 `current_time`（返回当前 UTC 时间）。MCP 本期不做。

**为什么**：内置示例的作用是**验证 SPI 的多来源分发真的能跑通**（`sourceType()` → 找到对应 provider → invoke）。只做 HTTP 的话，「按来源分发」这条逻辑一行真实用例都没有，等 MCP 接入时才发现问题。

**代价**：多写一个内置工具与一次目录同步逻辑。

**替代方案**：三类来源全做（含 MCP）—— 拒绝。CLAUDE.md §3.5 明确 MCP Java SDK 只算 Tier 2、「第 1 周必须做 spike」，本期一次做完风险偏高。

---

## 三、数据模型

### 3.1 表结构改动

`tool_definition` 现有列全部保留，**新增一列**：

```sql
ALTER TABLE `tool_definition`
  ADD COLUMN `config_version` BIGINT UNSIGNED NOT NULL DEFAULT 1
    COMMENT '工具配置版本，由业务层递增，用于乐观锁';
```

**为什么要 `config_version`**：工具配置（URL、请求头、入参 schema）同样会被两个人同时改。与 `ai_model_provider.config_version` 保持一致，更新走条件更新 `WHERE config_version = ?`，命中 0 行即冲突（40900）。

**索引**：新增 `idx_source_type_enabled (source_type, enabled)`，供目录列表按来源筛选并过滤启停。

### 3.2 `config` 列的结构（按 `source_type` 分化）

| source_type | `config` JSON 结构 | 敏感字段 |
|---|---|---|
| `http` | `{"url": "...", "method": "GET\|POST", "headers": {...}, "timeoutMs": 10000}` | `headers` 里的敏感值 |
| `mcp` | `{"mcpServerPublicId": "...", "mcpToolName": "..."}` | 无 |
| `builtin` | `{"providerBean": "builtinTimeProvider"}` | 无 |

`headers` 的**敏感值逐个加密**，密文以 `enc:v1:` 前缀标记：

```json
{
  "Content-Type": "application/json",
  "Authorization": "enc:v1:{\"version\":1,\"keyId\":\"tool-credential-v1\",\"nonce\":\"...\",\"ciphertext\":\"...\",\"tag\":\"...\"}"
}
```

**逐值加密而不是整个 headers 一起加密**：非敏感头（Content-Type 这类）保持明文，编辑表单能原样回显；敏感头回显为「已配置」，留空即保留。用户改 token 时不必重填其它头。

**敏感 key 的判定**：按名称匹配（`authorization` / `proxy-authorization` / `cookie` / `set-cookie` / `api-key` / `x-api-key` / `token` / `secret` / `password`），大小写不敏感。**用户不需要理解这个机制** —— 建工具时按规则自动加密。

### 3.3 名称与唯一性

| 约束 | 落点 | 理由 |
|---|---|---|
| `(source_type, tool_key)` 唯一 | 数据库唯一键（已有） | 工具的稳定标识 |
| `public_id` 唯一 | 数据库唯一键（已有） | 对外 ID |
| 工具 `name` 全局唯一 | **服务层校验**（查软删行），不加数据库唯一键 | 内置与 MCP 的 name 由代码 / 上游决定，加了唯一键会与目录同步的 upsert 打架 |
| `tool_key` 软删后不可复用 | 服务层校验时包含软删行，报错说清楚 | `uk_tool_key` 覆盖软删行 —— 与 Provider 的 `findIdByName` 同一个坑，处理方式一致 |

**创建 / 编辑 HTTP 工具时**：`name` 必须与**所有来源**的现有工具名不冲突（含 builtin / mcp），冲突报 40900「工具名已被占用（包括已删除记录）」。

**目录同步时**：若 MCP 工具名与已有行撞名，该新行落库但 `enabled=0`，同时记 warn 日志 —— 不阻断整批同步。

**软删 + 唯一键**：删除一个 HTTP 工具后，它的 `tool_key` 不能再建（唯一键覆盖软删行）。报错文案必须说明这一点，否则用户会以为是 bug。

### 3.4 生命周期

| 来源 | 行的来源 | 停用方式 | 删除方式 |
|---|---|---|---|
| `http` | 用户创建 | 用户切 `enabled=0` | 用户删除（软删） |
| `builtin` | 代码注册，启动/定时同步 upsert | 同步时发现代码里没有 → `enabled=0` | 不提供删除接口（代码管） |
| `mcp` | `tools/list` 同步 upsert | 同步时消失 → `enabled=0` | 不提供删除接口（上游管） |

**builtin / mcp 来源的行不提供用户删除入口** —— 它们由同步机制托管，用户删了下次同步又回来。

---

## 四、模块边界

### 4.1 依赖方向（不可违反）

```
hify-shared ← hify-tool ← hify-mcp
                 ↑
           hify-agent / hify-conversation / hify-workflow
```

- `hify-tool` **只依赖 `hify-shared`**（Maven 层面已经是）。
- `hify-tool` **不得依赖 `hify-mcp`** —— `ToolProvider` 接口由 `tool` 定义在 `api` 包，`mcp` 实现它。写成 `tool → mcp` 会成环。CLAUDE.md §2.2 与 code-organization spec §L6 / §M10 均已明确。
- `hify-tool` **不得依赖 `hify-ai`** —— 所以加密组件必须放 shared，不能复用 `ProviderCredentialCipher`。
- `hify-tool` **不得依赖 conversation / workflow**（ArchUnit `ModuleBoundaryTest` 的 M6 已在 CI 里拦）。

### 4.2 `api` 包的公开契约

`hify-tool/api` 只放 interface + 纯 DTO + 事件（CLAUDE.md §2.2）：

| 类型 | 作用 |
|---|---|
| `ToolProvider` | SPI：`sourceType()` / `listTools()` / `invoke(ctx, toolName, args)` |
| `ToolSpec` | 工具元数据：`toolKey` / `name` / `description` / `schema` / `sourceType` |
| `ToolResult` | 执行结果：`success` / `content` / `errorMessage` / `metadata` |
| `ToolInvokeContext` | 显式上下文：`userId` / `traceId`（§M7 —— 不用 ThreadLocal 跨模块） |
| `ToolCatalog` | 跨模块的目录接口：`listTools()` / `invoke(ctx, sourceType, toolKey, args)` |
| `ToolDefinitionService` | HTTP 工具的 CRUD（给管理台） |
| `ToolExecutionException` + `ToolErrorKind` | 工具执行失败的分类：`TIMEOUT` / `AUTH_FAILED` / `UPSTREAM_ERROR` / `TOOL_ERROR` |

**`api` 包不得出现 `mcp` 的任何类型**（否则 `tool ↔ mcp` 成环，spec §L6 明令）。

### 4.3 分层落位

| 层 | 内容 |
|---|---|
| `api` | 上表全部 |
| `internal/entity` | `ToolDefinitionDO`（继承 `shared.mybatis.BaseEntity`；`schema` / `config` 用 `JacksonTypeHandler` + `autoResultMap = true`） |
| `internal/mapper` | `ToolDefinitionMapper`（继承 `BaseMapper`）+ `findIdByToolKey`（含软删行）+ 按来源批量查 |
| `internal/converter` | `ToolConverter`（DO ↔ DTO；敏感头脱敏） |
| `internal/service` | `ToolDefinitionServiceImpl`（CRUD + 名称唯一性）、`ToolCatalogImpl`（聚合 SPI）、`ToolSyncService`（同步 upsert） |
| `internal/spi` | `BuiltinToolProvider`、`HttpToolProvider`（模块内部的来源实现，入参是 DO / id，不进 `api`） |
| `internal/controller` | `ToolController` |

**注意**：`http` 与 `builtin` 两个来源的实现放在 `hify-tool` 自己的 `internal/spi` 里 —— 它们不跨模块，没有放到 `api` 的理由。`mcp` 来源的实现将来放 `hify-mcp/internal/spi/McpToolProvider`。

### 4.4 HTTP 工具的执行

- **阻塞调用**（要等上游响应），**必须提交到 `llmExecutor`**，不占 Tomcat 线程（CLAUDE.md §3.5）。
- 超时：连接 5s / 读 30s（比 LLM 的 60s 短 —— 工具调用是链路里的一环，不该比整轮对话还慢）。
- 请求方法：一期支持 `GET` / `POST`。GET 时 `args` 的每个字段作为查询参数（对象 / 数组用 JSON 字符串）；POST 时 `args` 整体作为 JSON body。
- 响应体截断上限 8 KB，超出截断并在结果里标记。
- **不做入参 schema 校验** —— `schema` 只给模型看，模型传错参数由上游接口自己报错。

---

## 五、实现要点

### 5.1 目录同步机制

**触发**：

| 触发方式 | 覆盖场景 |
|---|---|
| `ApplicationReadyEvent` | 应用启动，同步全部来源一次 |
| 定时任务（每 10 分钟） | 兜底；多副本用 Redis 分布式锁 + 分钟窗口幂等键去重（与 Provider 健康检查同一套） |
| `McpToolListChangedEvent` | MCP 工具列表变化（本期无发射方，接口位留着） |

**算法**（对每个 `ToolProvider` 独立执行）：

```
specs = provider.listTools()
for spec in specs:
    upsert by (source_type, tool_key):
        name / description / schema / config  ← 覆盖
        enabled = 1
把该 source_type 下、本次未出现的 tool_key 全部置 enabled = 0
```

`upsert` 用 `INSERT ... ON DUPLICATE KEY UPDATE`，靠 `uk_tool_key` 命中。**不物理删除任何行** —— 目录行是「这个工具存在过」的记录，物理删会让引用它的 Agent 出现悬挂 id。

### 5.2 测试调用

`POST /api/v1/tools/{publicId}/test`，请求体带 `args`（JSON 对象）。

- 走 `ToolCatalog.invoke(ctx, sourceType, toolKey, args)`，与 Agent 实际调用**同一条代码路径** —— 测试通不代表能跑，但测试不通一定跑不了。
- 同步返回还是异步？HTTP 工具最长 30s，Tomcat 线程不能占（§3.5）。→ 用 `DeferredResult` + `llmExecutor`，与 Provider 的 `POST /{id}/test-connection` 同款（70s 超时）。前端 axios 超时 75s。

### 5.3 前端

| 项 | 落位 |
|---|---|
| 路由 | 新增 `/tools`，`meta.title = '工具管理'` |
| 菜单 | `App.vue` 的 `menus` 加一项（现在只有 models / agents / chat 三项） |
| 页面 | `hify-web/src/views/tool/ToolList.vue`（命名对齐 `ProviderList.vue`） |
| 类型 | `src/types/tool.ts` |
| API | `src/api/tool.ts` —— `getToolList` / `getToolDetail` / `createTool` / `updateTool` / `deleteTool` / `testTool` / `syncTools` |
| 表单 | `HifyFormDialog` + `el-tabs` 分三组：基础信息 / 请求配置 / 入参定义（`HifyFormDialog` 的默认插槽给了 `:form` / `:mode`，父组件自己放字段） |
| 敏感头 | 回显「已配置」，留空即保留 —— 与 Provider 的 `KEEP / REPLACE / CLEAR` 三态同款 |

**分页语义**：后端按 CLAUDE.md §3.3 用游标分页（`cursor` / `size` → `items` / `hasNext` / `nextCursor`），前端表格用 `HifyTable` 已有的 cursor 模式（Provider 那轮加的），**不要套页码 + total**。

---

## 六、验证方式

| 阶段 | 验证 |
|---|---|
| 数据模型 | `mvn -pl hify-app -am test -Dtest=AiSchemaMappingTest`；实体与 DDL 一致 |
| Entity + Mapper | `mvn -pl hify-tool -am test`；映射测试覆盖表名、列、主键、JSON TypeHandler、逻辑删除 |
| DTO | `mvn -pl hify-tool -am compile` + 校验注解单测 |
| Service | Mockito mock Mapper，断言分支与错误码：名称冲突 40900、tool_key 复用 40900、乐观锁冲突 40900、凭据缺失拒绝写入 |
| SPI | `ToolProvider` 三来源分发的单测；内置工具 invoke 的真实断言 |
| Controller | `mvn verify` + `curl` 打真实接口（正常路径 + 错误路径） |
| 前端 | `cd hify-web && npm test && npm run type-check && npm run build` |
| 完整验收 | curl 逐接口 + 浏览器全流程；测试数据删净，库回到验收前行数 |

---

## 七、注意事项

### 数据库

- **改 `schema.sql` ≠ 改了数据库。** 漏掉迁移脚本的症状是接口 500 + 日志 `Unknown column 'config_version' in 'field list'`。
- 迁移脚本写「无损边界」（不删表、不删列、不动数据），执行前 `mysqldump` 备份（权限 0600，确认被 git 忽略），执行后核对行数。

### 接口契约

- **`tool_definition` 没有 `public_id` 之外的对外标识** —— 路径参数用 `public_id`（ULID），与 Provider 一致。
- **分页语义先对齐** —— 游标分页，不要页码 + total。
- **敏感头不能回显** —— 详情接口返回 `headers` 时，敏感值替换成「已配置」标记，明文 / 密文都不给。

### 凭据

- **JSON TypeHandler 只做转换不加密。** 密文由 `shared.crypto.CredentialCipher` 产生，存在 `config.headers` 的值里。
- **密钥缺失即拒绝写入**，不能明文回退。
- **日志里不许出现 token。** 请求 / 响应体不进日志；异常 message 只留状态码，不带上游响应体（CLAUDE.md §3.5 的既有约定）。

### 并发

- **HTTP 工具调用是阻塞的**，必须走 `llmExecutor`，不占 Tomcat 线程。
- **目录同步在多副本下会重复执行** —— 定时同步用 Redis 分布式锁 + 幂等键去重（同 Provider 健康检查）。
- **同步的 upsert 不能物理删行** —— 目录行被 Agent 引用着。

### 前端

- **模板属性值里不能出现 `//`** —— vue-tsc 会当成注释报假错。URL / JSON 示例放 `<script setup>` 常量再绑定。
- **Element Plus 的隐藏列会多渲染一份副本** —— 自动化验证按可见性过滤。
- **敏感头输入框不能自动填充** —— 浏览器可能把明文缓存下来；留空即保留。

### 已知的下游影响（本期不处理）

- `ai_agent.tool_ids` 的多值索引写的是 `CAST(tool_ids->'$[*]' AS CHAR(64) ARRAY)`。统一目录后工具 id 是 **BIGINT**，存进去是数字数组，**CHAR(64) 的索引类型对不上** —— 这是 `hify-agent` 阶段的坑，届时要么改索引类型，要么让 `tool_ids` 存字符串形式的 id。本期不动。
- CLAUDE.md §2.4「不落库的」需要同步修改（见 §2.1）。

---

## 八、待确认

本文档待用户确认后进入阶段 1（数据模型 → schema.sql + 迁移脚本）。

已确认的四个决策：

| # | 决策 | 选择 |
|---|---|---|
| 1 | 目录表存哪些来源 | 三种来源全存（统一目录） |
| 2 | 凭据存储 | 抽到 shared 统一加密（Provider 暂不动） |
| 3 | 入参映射 | 整体作为 body / query |
| 4 | 一期来源范围 | HTTP + 一个内置示例 |
