# hify-agent 模块设计规格

> 阶段 0（咨询模式）的产出物。日期：2026-11-01 · 状态：已确认，直接进入实施
>
> **编号约定**：本文里 `§1.2` / `§2.1` / `§3.5` 指 **CLAUDE.md** 的章节；
> `§L6` / `§M6` 指 `docs/superpowers/specs/2026-10-29-hify-code-organization.md` 的条目；
> `§一` / `§二` 指本文自身的章节。

---

## 一、需求

### 1.1 模块定位

按 CLAUDE.md §2.1，`hify-agent` 负责 **Agent 配置的存取与组装**，归属表 `ai_agent`。
依赖 `shared`、`ai`、`tool`、`knowledge`。

CLAUDE.md §2.1 定的三条不变量，本模块全部要守：

- **`hify-agent` 只负责「把配置组装成一个可运行的 Agent」，不执行推理、不调用工具**
- 依赖方向：`ai + tool + knowledge → agent`，**不得反向**
- 组合逻辑（知识库注入、工具循环、记忆）不许出现在本模块

一期交付：

| # | 交付物 | 说明 |
|---|---|---|
| 1 | Agent 配置 CRUD | `ai_agent`：名称、描述、主模型、提示词、参数区、状态 |
| 2 | 绑定工具 | `tool_ids`：从统一目录（内置 / HTTP / MCP）里挑 |
| 3 | 绑定知识库 | `knowledge_ids`：从知识库列表里挑 |
| 4 | 状态流转 | `draft` / `published` / `disabled` |
| 5 | 组装接口 | `AgentCatalog.assemble(publicId)` → 一份可直接喂给编排层的 `AgentBundle` |
| 6 | 表单可选项 | 一次拿到「可选的 chat 模型 / 已启用工具 / 已启用知识库」 |
| 7 | 管理台页面 | Agent 列表、新增/编辑弹窗、删除 |

### 1.2 明确不做

- **发布快照**（`ai_agent_version`）—— CLAUDE.md §五 定了「一期不要，配置保存即生效」
- **多模型降级链** —— CLAUDE.md §3.5：降级链是**模型接入点配置**的一部分，不在 Agent 里
- **Agent 的执行**（推理、工具循环、知识库检索）—— 那是 `hify-conversation` / `hify-workflow` 的事
- **可视化编排** —— CLAUDE.md §1.2 明确不做

---

## 二、选型（决定了什么 / 为什么 / 替代方案）

### 2.1 引用存 `public_id`（字符串），不存内部主键

**决定**：`ai_agent.tool_ids` / `knowledge_ids` 存 ULID 字符串数组，不存 `tool_definition.id` / `kb_knowledge_base.id`。

**为什么**：

- 表上的多值索引是 `CAST(tool_ids->'$[*]' AS CHAR(64) ARRAY)` —— `CHAR(64)` 就是给 26 位的 ULID 留的位，存数字反而用不上这个索引形态
- 公开 ID 跨模块稳定。主键是自增的，一旦做数据迁移、导库、跨库同步，主键可能重排；引用断了就是「Agent 还绑着，工具没了」
- 出错时能直接说出是哪个工具 —— 数字主键对人没有意义

**代价**：字符串比数字大（26 字节 vs 8）。一个 Agent 绑 20 个工具，多约 360 字节 —— 可忽略。

**替代方案**：存内部主键 —— 已否决，主键不稳定。

### 2.2 状态三态：`draft` / `published` / `disabled`

**决定**：`status` 三态。编排层（对话、工作流）**只接受 `published`** 的 Agent。

**为什么**：表结构已经定成 `VARCHAR(32) DEFAULT 'draft'`，枚举三态正好对上。一期没有发布快照表，所以「发布」只是把 `status` 翻成 `published` —— **没有版本概念，改完直接生效**（CLAUDE.md §五「配置保存即生效」）。

`draft` 存在的意义是「还在配，先别用」：新建的 Agent 默认 `draft`，用户在管理台确认模型、提示词、工具都对了，再发布。

**代价**：多一步「发布」。对话页选 Agent 时只列 `published` 的，用户可能建完发现「对话里选不到」—— 所以列表页要给状态一个醒目的标签。

**替代方案**：只做 `enabled` 布尔 —— 会推翻已有 schema，且丢掉「草稿」这个有用的中间态。已否决。

### 2.3 组装边界：只组装，不执行

**决定**：`AgentCatalog.assemble(publicId)` 返回 `AgentBundle`（模型 + 工具规格 + 知识库清单 + 提示词 + 参数），**不发起任何模型调用、工具执行、知识库检索**。

**为什么**：CLAUDE.md §2.1 的不变量 ——「`hify-agent` 只负责把配置组装成一个可运行的 Agent，不执行推理、不调用工具」。执行发生在编排层。

**代价**：编排层拿到的是「零件」，还得自己发起检索与工具调用。这正是分层的目的。

### 2.4 只绑一个主模型

**决定**：`ai_agent.model_id` 单列，一个 Agent 一个主模型。

**为什么**：CLAUDE.md §3.5 明确「降级链是模型接入点配置的一部分，不在 Agent 里（Agent 的提示词是针对某模型调过的）」。

**代价**：一期做不了「一个 Agent 多模型兜底」。

### 2.5 参数区固定三个已知参数

**决定**：`param_config` 只认 `temperature` / `max_tokens` / `top_p` 三个参数，各有取值范围校验。

**为什么**：`param_config` 是 JSON 列，理论上能塞任意参数，但 CLAUDE.md §3.5 记了一条硬事实 ——「OpenAI 兼容是不支持的参数**静默丢弃**，不是报错」。允许自由扩展参数，等于允许用户填一个上游根本不认的参数，然后困惑于「为什么没生效」。

**替代方案**：任意 `Map<String,Object>` —— 未验证的参数会被静默丢弃，排查成本高。已否决。

**代价**：想加新参数要改代码。参数区本来就不是「用户随便填」的地方。

### 2.6 绑定时校验「存在且启用」

**决定**：保存 Agent 时，逐个校验绑定的工具与知识库**存在且处于启用状态**；绑定的模型必须存在、启用、且是 `chat` 用途。

**为什么**：绑一个停用的工具，Agent 运行时的工具清单会是空的 —— 模型以为有工具可用，调不动；绑一个 `embedding` 用途的模型，Agent 直接不可用。这些错在保存时拦住，比在对话时表现为一堆看不懂的现象强得多。

**代价**：上游配置后来被停用了，已经绑好的 Agent 会变得不可用。列表页因此要显示「引用缺失」的提示。

### 2.7 表单可选项由 agent 模块聚合

**决定**：新增 `GET /api/v1/agents/options`，一次返回可选的 chat 模型、已启用的工具、已启用的知识库。

**为什么**：「一个 Agent 能选什么」这个问题只有 agent 模块知道 —— 它同时依赖 ai、tool、knowledge 三个模块。前端分别打三个接口再拼，既碎又容易漏。

**代价**：options 接口的返回体会随工具/知识库数量增长。内部规模（几十个工具、几个知识库）下可以接受。

### 2.8 跨模块按 publicId 批量取

**决定**：给 `tool` 与 `knowledge` 的 api 各加一个「按 publicId 批量取」的方法：

- `ToolDefinitionService.listBindingsByPublicIds(List<String>)` → `List<ToolBinding>`
- `KnowledgeBaseService.listByPublicIds(List<String>)` → `List<KnowledgeBaseSummaryResp>`

`ai` 侧加一个 `ModelService.listByIds(Collection<Long>)` —— 模型在 `ai_agent` 里存的是内部主键，跨模块只存 id（CLAUDE.md §2.4），要显示模型名就得按主键反查。

**为什么**：组装时要按绑定的 id 取工具规格与知识库清单，逐个 `get()` 是 N+1 次跨模块调用；而 `list()` 是游标分页，一次拿不全（上限 200）。

**代价**：三个模块的 api 各多一个方法。方法本身很薄（一次 `IN` 查询），换来的是组装路径没有 N+1。

---

## 三、数据模型

### 3.1 `ai_agent` 新增两列 / 一个唯一键

```sql
ALTER TABLE `ai_agent`
  ADD COLUMN `config_version` BIGINT UNSIGNED NOT NULL DEFAULT 1
    COMMENT 'Agent 配置版本，由业务层递增，用于乐观锁',
  ADD UNIQUE KEY `uk_name` (`name`);
```

**为什么**：

- `config_version` —— 与 `ai_model_provider` / `ai_model` / `kb_knowledge_base` / `tool_definition` / `mcp_server` 的做法一致。Agent 的模型绑定、工具绑定都会被改，两个管理员同时改会互相覆盖。
- `uk_name` —— `ai_agent` 是**唯一**一个还没有 `uk_name` 的具名配置实体（provider / model / mcp_server / kb_knowledge_base 都有）。名称在管理台是给人看的，重名会让「我刚才建的那个」变得难找。

**唯一键覆盖软删行的后果**：删掉的 Agent 名称不能再用，报错要说清楚（含「包括已删除记录」）—— 与 `mcp_server` / `kb_knowledge_base` 的处理一致（CLAUDE.md §3.3）。

### 3.2 `ai_agent_version` 一期不动

保留在 `schema.sql` 里不删 —— CLAUDE.md §五 说「真需要时是纯新增表」。但**代码里不建对应实体与 Mapper**，也不写它的迁移脚本。

### 3.3 不动的部分

`public_id` / `name` / `description` / `model_id` / `prompt_template` / `tool_ids` / `knowledge_ids` / `param_config` / `status` / `app_type` / `created_at` / `updated_at` / `created_by` / `deleted` 全部保留原样。

`app_type` 一期恒为 `chat`（CLAUDE.md §四「只做一种流程形态（Chatflow 语义）」），请求里不开放这个字段。

---

## 四、模块边界

### 4.1 依赖方向

```
hify-shared ← hify-ai ← hify-tool ← hify-knowledge
                    ↘  hify-agent  ↗
```

- `hify-agent` 依赖 `shared`、`ai`、`tool`、`knowledge`
- **不依赖** conversation / workflow（编排层）
- `ai` / `tool` / `knowledge` **不依赖** agent —— 反过来会成环

### 4.2 `api` 包的公开契约

| 类型 | 作用 |
|---|---|
| `AgentService` | 管理台用的 CRUD |
| `AgentCatalog` | 编排层用的组装接口 |
| `AgentSummaryResp` | 列表项摘要 |
| `AgentDetailResp` | 详情（含提示词、绑定项、参数区） |
| `AgentCreateReq` / `AgentUpdateReq` | 增改请求 |
| `AgentListQuery` / `AgentListResp` | 列表（游标分页） |
| `AgentBundle` | 组装结果：模型 + 工具规格 + 知识库清单 + 提示词 + 参数 |
| `AgentParamConfig` | 参数区（temperature / maxTokens / topP） |
| `AgentStatus` | 状态枚举：draft / published / disabled |
| `AgentOptions` | 表单可选项（模型 / 工具 / 知识库） |

### 4.3 分层落位

| 层 | 内容 |
|---|---|
| `api` | 上表全部 |
| `internal/entity` | `AgentDO`（继承 `BaseEntity`） |
| `internal/mapper` | `AgentMapper` |
| `internal/converter` | `AgentConverter` |
| `internal/service` | `AgentSupport`（校验）、`AgentServiceImpl`、`AgentAssembler`（组装） |
| `internal/controller` | `AgentController` |

**没有 `internal/spi`** —— 本模块不定义扩展点，SPI 都在消费方的 api 包里（`ToolProvider` 在 tool，`ProviderAdapter` 在 ai 的 internal）。

---

## 五、实现要点

### 5.1 引用存法

DO 里 `tool_ids` / `knowledge_ids` 是 `List<String>`，用 `JacksonTypeHandler` 序列化；`param_config` 是 `Map<String, Object>`，同样用 `JacksonTypeHandler`。

实体要 `@TableName(value = "ai_agent", autoResultMap = true)` —— JSON 列的 TypeHandler 依赖它。

### 5.2 名称唯一性检查

唯一键覆盖软删行，所以检查占用要用**不带 `deleted = 0`** 的查询（`@Select` 自定义 SQL），否则删掉同名再建会撞库报错而不是给出清晰提示。

### 5.3 乐观锁

更新时 `WHERE id = ? AND config_version = ?`，命中 0 行抛 `CONFLICT`（「Agent 配置已变更，请刷新后重试」）。

### 5.4 组装

```
AgentAssembler.assemble(publicId)
  → 读 ai_agent 行（published 才继续）
  → model:   ModelService.get(modelPublicId)                    → ModelSummaryResp
  → tools:   ToolDefinitionService.listSpecsByPublicIds(toolIds) → List<ToolSpec>
  → kbs:     KnowledgeBaseService.listByPublicIds(kbIds)        → List<KnowledgeBaseSummaryResp>
  → 组装 AgentBundle
```

组装**不做任何上游调用** —— 只查本地配置表与目录表。

### 5.5 绑定校验

保存时把「请求里声明的 id 集合」与「上游查得到的 id 集合」对比，差的报 `PARAM_ERROR` 并点名是哪个：

```
工具不存在或已停用：01J...
```

模型还要额外校验 `modelType == "chat"` 且 `enabled`。

---

## 六、验证方式

| 阶段 | 验证 |
|---|---|
| 数据模型 | `mvn -pl hify-app -am test -Dtest=AiSchemaMappingTest`；迁移脚本执行前后核对行数 |
| Entity + Mapper | `mvn -pl hify-agent -am test`（映射测试：表名、列、主键、JSON TypeHandler、逻辑删除） |
| DTO | `mvn -pl hify-agent -am compile` + 校验注解单测 |
| Service | Mockito mock Mapper，断言状态流转、乐观锁、引用校验、名称唯一 |
| Assembler | Mockito mock 三个下游模块，断言组装结果与「非 published 拒绝」 |
| Controller | `mvn verify` + `curl` 打真实接口 |
| 前端 | `cd hify-web && npm test && npm run type-check && npm run build` |
| 完整验收 | curl 逐接口 + 浏览器全流程；测试数据删净 |

---

## 七、注意事项

### 依赖与边界

- **本模块不执行任何东西** —— 看到 `llmExecutor`、HTTP 客户端、MCP 客户端出现在 agent 模块，就是越界了。
- **不要让 `ai` / `tool` / `knowledge` 反向依赖 `agent`**。

### 跨模块调用

- **只走对方的 `api` 包**，不 import 对方的 `internal`，不直接查对方的表。
- **批量取，不逐个 get** —— 组装路径上 N+1 次跨模块调用会把一次对话的装配拖成几十次往返。

### 数据

- **JSON 列的 TypeHandler 只做转换，不加密**（本模块没有凭据字段，不涉及）。
- **`tool_ids` 存字符串** —— 往 JSON 里塞数字会让多值索引的形态对不上。

### 验证纪律

- **每层验证通过再进下一层。**
- **不要把验收用的测试数据留在库里**；删完核对行数。

---

## 八、实现期发现

> （实现过程中遇到的实际问题，实时补记。）

### 8.1 `ToolSpec` 里没有 `public_id`，得另开一个返回类型

**症状**：组装 Agent 工具清单时，没法把「Agent 绑定的 id」对上「工具规格」。

**原因**：`ToolSpec` 是 `ToolProvider` 产出的（provider 只知道 `toolKey`，不知道目录行的 `public_id`），所以它没有也不该有 `public_id`。

**做法**：不在 `ToolSpec` 上加字段（provider 填不出来），而是让 tool 模块按 id 查**目录行**——目录表本身就是真相来源，那一行既有 `public_id` 也有 `tool_key`。返回类型定成新的 `ToolBinding(publicId, enabled, spec)`：

- `publicId` —— 调用方要能把「查到的」与「请求的」对上
- `enabled` —— 调用方要能区分「不存在」与「已停用」，两者的提示不一样
- `spec` —— 组装 function calling 的 tools 定义要用的

只返回 `List<ToolSpec>` 是不够的：既对不上号，也分不清停用。

### 8.2 更新时只校验「这次显式提交过」的绑定

**症状**：某个绑定的工具被停用之后，用户只想改个名字，保存却被「工具已停用」挡下来。

**做法**：更新请求里 `toolIds` / `knowledgeIds` 为 `null` 表示「沿用原值」，**这时不重新校验**；只有显式提交了列表才校验。

- 显式提交 → 校验（用户确实在改绑定）
- 沿用原值 → 不校验（原绑定坏掉是历史问题，不该阻塞无关改动）

坏掉的绑定由列表页的「N 项不可用」提示，不靠保存时报错。

### 8.3 `ai_agent.model_id` 存内部主键，跨模块只能按主键反查

**症状**：`ai_agent` 里存的是 `ai_model.id`（BIGINT），但 `ModelService` 原本只认对外 ID。

**做法**：给 ai 的 api 加 `listByIds(Collection<Long>)`。这是**跨模块只存 id**（CLAUDE.md §2.4）的必然结果 —— 存了主键就要能按主键反查。

顺带解决列表页的 N+1：一页 Agent 的模型用一次批量查询全解析出来，而不是按行逐个查。

### 8.4 Mockito 的 `anyList()` 匹配不了 `Set`

**症状**：单测报 `PotentialStubbingProblem`，说 `listByIds([7])` 与打桩的 `listByIds([])` 参数不匹配。

**原因**：`anyList()` 做的是**类型检查**（必须是 `List`），而生产代码传的是 `HashSet`。桩上记录的值就是 `anyList()` 的默认值 `[]`，看着像「参数变了」，其实是类型不匹配。

**做法**：接口签名是 `Collection`，打桩就用 `anyCollection()`。

**教训**：打桩的匹配器要和方法的**声明类型**一致 —— `anyList()` / `anyString()` / `anySet()` 都是带类型检查的，不是「任意值」。
