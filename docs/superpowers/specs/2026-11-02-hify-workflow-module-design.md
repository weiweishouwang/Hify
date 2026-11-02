# hify-workflow 模块设计规格

> 阶段 0（咨询模式）的产出物。日期：2026-11-02 · 状态：已确认，直接进入实施
>
> **编号约定**：本文里 `§1.2` / `§2.1` / `§3.5` 指 **CLAUDE.md** 的章节；
> `§L6` / `§M6` 指 `docs/superpowers/specs/2026-10-29-hify-code-organization.md` 的条目；
> `§一` / `§二` 指本文自身的章节。

---

## 一、需求

### 1.1 模块定位

按 CLAUDE.md §2.1，`hify-workflow` 负责 **工作流定义的存取与执行（6 种节点）**，
归属表 `wf_definition`、`wf_run`。依赖 `shared`、`ai`、`tool`、`knowledge`、`agent`。

CLAUDE.md §1.2 定的范围：**只支持 JSON 配置；节点 6 种：LLM / Agent / 工具 / 知识检索 / If-Else / 输出**。
§4 定的产品决策：**砍掉可视化拖拽** —— 画布是 Dify 前端成本的大头（1619 文件 vs 引擎 95 文件），
一人做要 3–4 周而核心链路不需要。

一期交付：

| # | 交付物 | 说明 |
|---|---|---|
| 1 | 定义 CRUD | `wf_definition`：名称、描述、JSON 定义、版本、状态 |
| 2 | 定义校验 | 节点类型、连线完整、无环、恰有入口、字段齐备 |
| 3 | 执行引擎 | 6 种节点按连线走，变量在节点间传递 |
| 4 | 模板解析 | `{{变量}}` 插值：运行入参 + 上游节点的输出 |
| 5 | 运行记录 | `wf_run`：状态、出入参、执行节点数、耗时、失败原因 |
| 6 | 管理台页面 | 列表、JSON 编辑器、运行面板 |

### 1.2 明确不做

- **可视化画布** —— CLAUDE.md §4 已否决
- **并行分支 / 汇聚节点** —— 一期按连线顺序**串行**执行
- **循环** —— 定义校验阶段拒绝有环的图
- **定时触发 / webhook 触发** —— 一期只支持手动触发
- **工作流版本快照** —— `version` 每次更新加一，用作乐观锁；不留历史副本
- **Agent 节点里的工具循环** —— 见 §2.6

---

## 二、选型（决定了什么 / 为什么 / 替代方案）

### 2.1 工作流 JSON 的形状 —— 节点数组 + 连线数组

**决定**：

```json
{
  "model": "01MODEL...",
  "nodes": [
    {"id": "n1", "type": "llm", "prompt": "...", "output": "answer"},
    {"id": "n2", "type": "output", "value": "{{answer}}"}
  ],
  "edges": [{"from": "n1", "to": "n2"}]
}
```

**为什么**：表结构里 `definition` 列的注释就写着「工作流 JSON：**节点与连线**」。
节点数组管「每个节点干什么」，连线数组管「先跑谁后跑谁」，两件事分开。

**代价**：线性工作流写起来啰嗦（每个节点都要一条连线）。

**替代方案**：每个节点带一个 `next` 字段 —— 线性流程更短，但分支要写 `next` 数组，
表达力反而更弱，而且和表结构的注释对不上。**已否决。**

### 2.2 变量传递 —— `{{变量名}}` 插值 + 一个扁平变量表

**决定**：模板语法 `{{变量名}}`。变量表是扁平的 `Map<String, Object>`：

- `input.<字段名>` —— 本次运行的入参
- `<节点的 output 变量名>` —— 上游节点的输出

**为什么**：不引表达式语言。表达式引擎（SpEL / JEXL / 自研）要么多一个依赖，
要么自己写解析器 —— 两种都在「只支持 JSON 配置」的降级定位里过重。

**代价**：只能取变量、不能算表达式。要算数用 `if` 节点的比较运算符。

**替代方案**：SpEL —— 已否决（多一个依赖 + 转义地狱 + 沙箱问题）。

### 2.3 If-Else 的条件 —— 显式的「左值 运算符 右值」

**决定**：

```json
{"id": "n3", "type": "if", "left": "{{answer}}", "operator": "contains", "right": "错误",
 "then": "n4", "otherwise": "n5"}
```

运算符：`equals` / `not_equals` / `contains` / `not_contains` / `is_empty` / `not_empty` /
`gt` / `lt`。

**为什么**：条件判断是工作流里最容易出错的地方。显式三段式没有歧义，
读 JSON 就知道会怎么判；表达式字符串（`"{{answer}} contains '错误'"`）看着自然，
但要写解析器、要定优先级、报错时看不出是哪一段错了。

**代价**：不支持「A 且 B」这种复合条件 —— 一期用嵌套的 if 节点代替。

### 2.4 执行模型 —— 顺着连线走，每个节点最多执行一次

**决定**：入口节点 = 入度为 0 的节点（**必须恰好一个**）。从入口开始执行，
每个节点执行完沿出边前进；`if` 节点只走命中的那条边。
**已经执行过的节点再被走到就跳过** —— 这就是汇聚（merge）的语义。

**为什么**：顺着连线走跟人读 JSON 的顺序一致；「已执行过就跳过」让汇聚节点不用
专门设计，两条路汇到同一个节点时它只跑一次。

**代价**：两个分支都往同一个节点汇、而那个节点依赖两边的输出时，后到的输出会晚到 ——
一期不做这种用法（汇聚节点只用其中一边的变量）。

**替代方案**：拓扑排序后逐个执行 —— 已否决：分支没走到的下游也会被执行，
语义不对（用户明明走了 else 分支，另一支的节点也跑了）。

### 2.5 工作流的模型 —— 顶层 `model` 默认，节点可覆盖

**决定**：定义顶层可以写 `model`（模型的 **publicId**），`llm` 节点可以用自己的
`model` 覆盖。`agent` 节点用 Agent 自带的模型。

**为什么**：绝大多数工作流只有一个模型，顶层写一次就够；个别节点换模型时才覆盖。

**代价**：JSON 里写的是 26 位 ULID，人读不懂。**缓解**：管理台的工作流编辑器带一个
模型选择器，选中后把 publicId 填进 JSON；文档里也写清楚。

**替代方案**：用模型的上游标识（`model_name`）—— `ai_model.model_name` 在同一个接入点下
唯一、跨接入点不唯一，会撞名。**已否决。**

### 2.6 Agent 节点不带工具循环

**决定**：`agent` 节点 = 用 Agent 的模型 + 提示词 + 知识库做**一次**模型调用。
不跑工具循环。

**为什么**：CLAUDE.md §2.1 写死了「`hify-workflow` 与 `hify-conversation` 互不依赖
（两者是平行的编排层）」。工具循环是 conversation 模块的编排逻辑；
在 workflow 里再写一份，就是同一套逻辑两份实现，而且两边的「工具函数名映射」
规则得各自维护 —— 改一处忘一处，模型就会调错工具。

**代价**：Agent 绑的工具在 workflow 的 agent 节点里用不上。
**缓解**：工作流有独立的 `tool` 节点，要调工具直接用它。

**替代方案**：在 workflow 里也实现工具循环 —— 已否决（重复实现 + 边界破坏）。

### 2.7 知识检索节点 —— 输出片段文本列表

**决定**：`knowledge` 节点调 `knowledge.api` 的检索，输出是**分段正文的列表**。

**为什么**：下游节点（尤其是 llm 节点）要把它塞进提示词。存原始 `SearchResult`
（含分数、chunk id）会让模板取值变复杂。

**代价**：引用信息（chunk id / 文档名）在工作流里丢失 —— 工作流的定位是「跑出结果」，
不是「可溯源的对话」；要溯源用对话模块。

### 2.8 运行是同步的，但跑在 llmExecutor 上

**决定**：`POST /workflows/{id}/run` 用 `DeferredResult` —— controller 立刻返回，
工作流在 `llmExecutor` 上跑，跑完把结果塞回 `DeferredResult`。

**为什么**：工作流里可能有多个模型调用，全是阻塞的（CLAUDE.md §3.5）。
跑在 Tomcat 线程上会把工作线程占死。

**代价**：HTTP 连接一直挂着（最长 5 分钟）。**替代方案**：SSE 逐步推进度 ——
已否决（一期不需要看中间进度，跑完给结果就行）。

### 2.9 数据模型不需要改

**核对结论**：`wf_definition` 与 `wf_run` 的字段够用，`schema.sql` 里也**已经带上了
`wf_run` 的按月分区定义**。

- `wf_definition.definition` 是 JSON 列 —— 定义整体存这儿
- `wf_run` 没有 `deleted` —— 事件流表，不继承 `BaseEntity`
- `wf_run.step_count` 记执行了几个节点

**代价**：没有代价。**收益**：事件流表是分区表，加列要重建整张表 —— 能不加就不加。

---

## 三、数据模型

### 3.1 `wf_definition` —— 不动

`public_id` / `name` / `description` / `definition` / `version` / `status` /
`created_by` / 审计字段 / `deleted` 全部保留。`uk_name` 覆盖软删行 → 报错要说清楚
（含「包括已删除记录」）。

**`version` 一列兼两职**：既是「这是第几版」，也是乐观锁。表上只有这一个版本列
（见 §8.5），不新增 `config_version`。

### 3.2 `wf_run` —— 不动

`definition_id` / `status` / `input` / `output` / `step_count` / `error_message` /
`latency_ms` / `created_at` 全部保留。

**不加 `public_id`** —— 运行记录不对外暴露为资源，接口按「某个工作流的运行列表」给。

### 3.3 不写迁移脚本

线上库与 `schema.sql` 已经一致。

---

## 四、模块边界

### 4.1 依赖方向

```
hify-shared ← hify-ai ← hify-tool ← hify-knowledge ← hify-agent
                                    ↘              ↙
                              hify-workflow（编排层）
```

- `hify-workflow` 依赖 shared、ai、tool、knowledge、agent
- **不依赖** conversation（两个编排层平行）
- ai / tool / knowledge / agent **不依赖** workflow

### 4.2 `api` 包的公开契约

| 类型 | 作用 |
|---|---|
| `WorkflowService` | 定义 CRUD + 运行 |
| `WorkflowDefinition` / `WorkflowNode` / `WorkflowEdge` | JSON DSL 的类型化模型 |
| `WorkflowNodeType` | 六种节点类型 |
| `WorkflowSummaryResp` / `WorkflowDetailResp` | 列表项与详情 |
| `WorkflowCreateReq` / `WorkflowUpdateReq` | 增改请求 |
| `WorkflowListQuery` / `WorkflowListResp` | 列表（游标分页） |
| `WorkflowRunReq` / `WorkflowRunResp` / `WorkflowRunSummaryResp` | 运行相关 |

### 4.3 分层落位

| 层 | 内容 |
|---|---|
| `api` | 上表全部 |
| `internal/entity` | `WorkflowDefinitionDO`（继承 `BaseEntity`）、`WorkflowRunDO`（不继承） |
| `internal/mapper` | `WorkflowMapper`、`WorkflowRunMapper` |
| `internal/converter` | `WorkflowConverter` |
| `internal/service` | `WorkflowSupport`（校验）、`WorkflowServiceImpl`、`WorkflowEngine`、`TemplateResolver` |
| `internal/controller` | `WorkflowController`、`WorkflowRunDispatcher` |

---

## 五、实现要点

### 5.1 定义的校验

保存时逐条检查：

- 节点 id 唯一、非空
- 节点类型在六种之内，且该类型的必填字段齐备
- 每条连线的 from / to 都存在
- **无环**（拓扑排序）
- 恰好一个入口节点（入度为 0）
- 至少一个 `output` 节点
- `if` 节点的 `then` / `otherwise` 指向存在的节点

**为什么在保存时校验**：定义是用户手写的 JSON，跑起来才发现少个字段，
排查成本高得多。

### 5.2 模板解析

`{{变量名}}` —— 变量名允许含字母、数字、下划线、点和短横线。
解析时先整体替换已知的变量，剩下的 `{{...}}` 原样保留（不报错）——
这样「提示词里正好有 `{{` 」这种情况不会炸。

### 5.3 节点执行

| 节点 | 输入 | 输出 |
|---|---|---|
| `llm` | `prompt` 模板 + 模型 + 参数 | 模型生成的文本 |
| `agent` | `input` 模板 + Agent 的模型与提示词 | 模型生成的文本 |
| `tool` | `tool`（工具 publicId）+ `args` 模板 | 工具结果文本 |
| `knowledge` | `knowledgeBase`（publicId）+ `query` 模板 + `topK` | 分段正文列表 |
| `if` | `left` / `operator` / `right` | 布尔值（不落变量表，只用来选分支） |
| `output` | `value` 模板 | 落到运行出参 |

### 5.4 事务边界

- **写库的每个动作单独一个事务** —— 工作流执行里有模型调用，不能包进事务
  （CLAUDE.md §3.1 E5）
- 落 `wf_run`（running）是独立一次写
- 跑完更新 `wf_run`（succeeded / failed）是独立一次写

### 5.5 失败处理

- 某个节点失败 → 整轮失败，`wf_run` 记 failed + 失败原因，HTTP 层返回 200（工作流跑过了，
  失败信息在 payload 里）
- 模型调用失败 → 转成节点错误，记下是哪个节点
- 步骤数超上限（100）→ 判定为失败，防止定义里的环把机器跑满

---

## 六、验证方式

| 阶段 | 验证 |
|---|---|
| 数据模型 | 核对 `wf_definition` / `wf_run` 与实体一致；分区定义与线上库逐字比对 |
| Entity + Mapper | `mvn -pl hify-workflow -am test`（映射测试：表名、列、主键、逻辑删除标记） |
| DTO | `mvn -pl hify-workflow -am compile` + 校验注解单测 |
| 校验 | 单测覆盖：有环、无入口、字段缺失、未知类型、id 重复 |
| 引擎 | 单测覆盖：六种节点各一个、模板插值、条件分支、汇聚节点只跑一次、步骤上限 |
| Service | Mockito mock Mapper，断言状态流转、名称唯一、乐观锁 |
| Controller | `mvn verify` + `curl` 打真实接口 |
| 前端 | `cd hify-web && npm test && npm run type-check && npm run build` |
| 完整验收 | curl 逐接口 + 浏览器全流程；测试数据删净 |

**验收需要一个真的模型** —— 复用对话模块验收时写的那个本地 mock
（`/tmp/hify_mock_llm.py`，OpenAI 兼容的 SSE）。

---

## 七、注意事项

### 依赖与边界

- **workflow 不碰 conversation** —— 两个编排层平行。
- **不重复实现工具循环** —— 见 §2.6。
- **只走别人的 api 包**，不查别人的表。

### 线程

- **运行在 `llmExecutor` 上**（模型调用是阻塞的），controller 用 `DeferredResult` 立即返回。

### 数据

- **定义是用户手写的 JSON** —— 保存时校验，运行时再校验一次（定义可能被并发改）。
- **`wf_run` 不继承 `BaseEntity`** —— 事件流表，没有 `deleted` 列。

### 验证纪律

- **每层验证通过再进下一层。**
- **不要把验收用的测试数据留在库里**；删完核对行数。

---

## 八、实现期发现

> （实现过程中遇到的实际问题，实时补记。）

### 8.1 输出节点的变量名不必唯一

**症状**：条件分支的两条路上各有一个输出节点，两个都用 `output: "result"`，
校验报「输出变量名重复」—— 但这恰恰是**应该**这么写的：只会有一条路跑起来。

**做法**：输出节点的名字**不查重**。查重只针对进变量表的节点
（模型调用 / Agent / 工具 / 知识检索）—— 那些是变量，名字重了会互相覆盖。

**教训**：查重规则要按「名字落在哪个命名空间里」来定，不是按「同一个字段」一刀切。

### 8.2 用户 id 必须显式带进编排线程

**症状**：写完编排器才发现，工具调用取到的 user id 永远是「开发用户」——
编排跑在 `llmExecutor` 上，那边的 `CurrentUserHolder` 是空的
（CLAUDE.md §3.1 C5：线程池复用会串号）。

**做法**：在 HTTP 线程上把用户 id 取出来，**显式传进**编排器。
对话模块也有同样的问题，一并改了。

**为什么不能靠 ThreadLocal**：执行线程是从池里拿的，上一个任务留下的
`CurrentUserHolder` 会被下一个任务读到 —— 串号比取不到更糟。

### 8.3 模板整体是一个占位符时，返回原始值而不是字符串

**症状**：知识检索节点的输出是「分段正文列表」，下游用 `{{docs}}` 引用 ——
按普通字符串插值会把它拼成一段文本，列表结构就丢了。

**做法**：整个模板**就是一个**占位符时，返回变量的原始值（列表还是列表、
数字还是数字）；夹在文本中间时才做字符串插值。

**代价**：同一个变量在两种位置引用的结果类型不一样（列表 vs 字符串）。
这是刻意的 —— 「要这个值本身」和「要把它写进一句话里」是两种不同的需求。

### 8.4 模板里解析不到的变量原样保留

**症状**：提示词里正好有 `{{` 这种东西时，整轮工作流会炸。

**做法**：解析不到的占位符**原样保留**，不报错、不替换成空串。
工作流的模板不是模板引擎，猜不出作者想干什么 —— 保留原文比猜错强。

### 8.5 `wf_definition` 没有 `config_version`，`version` 一列兼两职

**症状**：照着别的模块的惯例给工作流加了 `config_version` 做乐观锁，
一存就报 `Unknown column 'config_version' in 'field list'`。

**原因**：`wf_definition` 是本项目里唯一用 `version` 而不是 `config_version`
做版本列的配置表 —— 它只有一个版本列。

**做法**：**用 `version` 这一列做乐观锁**，不新增列。理由：
一期没有版本历史可查，「这是第几版」这个语义本身就没什么用，
而乐观锁能防住「两个人同时改 JSON 后一个覆盖前一个」——
后者是实打实的风险。

**连带**：接口字段从 `configVersion` 改成 `version`（跟列名对齐，少一次心智映射）。
**教训**：新增模块前先看一遍目标表到底有哪些列 —— 别把别的模块的约定想当然地套上去。
