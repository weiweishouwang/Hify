# hify-knowledge 模块设计规格

> 阶段 0（咨询模式）的产出物。日期：2026-11-01 · 状态：已确认，直接进入实施
>
> **编号约定**：本文里 `§1.2` / `§2.4` / `§3.3` 指 **CLAUDE.md** 的章节；
> `§L6` / `§M6` 指 `docs/superpowers/specs/2026-10-29-hify-code-organization.md` 的条目；
> `§一` / `§二` 指本文自身的章节。

---

## 一、需求

### 1.1 模块定位

按 CLAUDE.md §2.1，`hify-knowledge` 负责 **TXT 上传、固定分块、embedding、向量检索**，是**唯一同时访问 MySQL 与 PostgreSQL 的模块**。归属表 `kb_knowledge_base`、`kb_document`（PG: `vec_chunk`）。

CLAUDE.md §1.2 把知识库定为**降级**级别：**只支持 TXT 上传 + 固定分块 + 向量检索**。

一期交付：

| # | 交付物 | 说明 |
|---|---|---|
| 1 | 知识库 CRUD | MySQL `kb_knowledge_base`：名称、描述、embedding 模型、分块参数、启停 |
| 2 | 文档上传 | multipart 上传 TXT，落 `kb_document`（状态机 uploaded → indexing → ready / failed） |
| 3 | 固定分块 | 按字符切，块大小与重叠来自知识库配置 |
| 4 | 向量化 | 调 `hify-ai` 的 embedding 能力 |
| 5 | PG 写入 | 分段正文 + 向量写 `vec_chunk`，幂等 upsert |
| 6 | 检索 | query 向量化 + HNSW 余弦检索 top-k，供编排层用 |
| 7 | 管理台页面 | 知识库列表、文档列表、TXT 上传、状态展示、删除、检索测试 |

### 1.2 明确不做

- 非 TXT 格式（PDF / Word / Markdown 渲染）—— CLAUDE.md §1.2 明确只支持 TXT
- 智能切分（按语义 / 句子）—— 只做固定分块
- 文档重新索引的 UI（后台有兜底任务）
- 多租户 / 知识库共享

---

## 二、选型（决定了什么 / 为什么 / 替代方案）

### 2.1 `hify-ai` 的依赖增量 —— **补模型管理 + embedding 调用**

**决定**：在 `hify-ai` 增加两块能力 ——（a）模型管理：从接入点同步上游模型到 `ai_model`，供管理台选；（b）`EmbeddingService`：按 `ai_model.public_id` 向量化一批文本。

**为什么**：知识库要能向量化，就必须有一个**已存在的 embedding 模型**，而 `hify-ai` 目前只有 Provider CRUD，没有任何写 `ai_model` 的入口 —— 数据库里的模型行只能靠手写 SQL 塞进去。同一个缺口也让 Provider 页的「模型数」列永远显示 0。

**替代方案**：
- 把模型管理推到下一轮 —— 知识库交付后仍然选不到 embedding 模型，模块无法真正可用。**已否决。**
- 在 `hify-knowledge` 里直接查 `ai_model` 表 —— 违反 CLAUDE.md §3.2（跨模块只能走 `api` 包）。**已否决。**

**代价**：`hify-ai` 多两块能力；`ProviderAdapter` SPI 多一个 `embed()` 方法（Anthropic 不提供 embedding，默认实现直接拒绝）。

### 2.2 embedding 走协议适配器 —— **在 `ProviderAdapter` 上加 `embed()`

**决定**：`ProviderAdapter` 增加 `embed(provider, modelName, texts)`。`AbstractCatalogAdapter` 给默认实现（抛「该协议不支持 embedding」），`OpenAiAdapter` 与 `OllamaAdapter` 覆写。

**为什么**：embedding 的路径与请求体结构和 chat 不同，但**鉴权头、base_url、错误分类**完全一致 —— 复用适配器就不必再写一套 HTTP 与凭据逻辑。

**替代方案**：单独写一个 `EmbeddingAdapter` SPI + 单独一套工厂 —— 两套注册点，漏一处就是「保存成功但调用永远失败」。**已否决。**

**代价**：Anthropic 档的 `embed()` 只会抛异常。这是对的 —— Anthropic 确实没有 embedding 接口。

**协议细节**：
- openai 档：`POST {base}/embeddings`，体 `{"model": ..., "input": [...]}`，响应 `data[].embedding`
- ollama 档：`POST {base}/api/embeddings`，体 `{"model": ..., "prompt": "..."}`（原生接口一次一条），响应 `embedding`

### 2.3 `embedding_model` 存什么 —— **存 `ai_model.public_id`**

**决定**：`kb_knowledge_base.embedding_model` 存 embedding 模型的 `ai_model.public_id`（ULID，列宽 128 够用）。

**为什么**：上游 `model_name` **跨接入点会重名**（唯一键是 `(provider_id, model_name)`），只存 model_name 无法唯一定位到接入点 —— 而 embedding 请求要发到哪个 provider 是关键。存 public_id 才能唯一定位，且跨模块引用用对外 id，不暴露内部自增主键。

**替代方案**：存上游 model_name（自解释，但有歧义）；存 ai_model.id（BIGINT，与 `ai_agent.tool_ids` 一致，但暴露内部 id）。**均已否决。**

**代价**：列注释要改（`VARCHAR(128)` 不变，只改 COMMENT）—— 无损变更。

### 2.4 向量维度 —— **固定 1536**

**决定**：知识库创建时选定的 embedding 模型，其维度必须等于 1536（`vec_chunk.embedding vector(1536)` 建表时定死）。

**为什么**：PG 不支持 `ALTER` 改 vector 维度，换维度必须重建 `vec_chunk` 表。

**代价**：Ollama 常用的 nomic-embed-text（768 维）用不了。一期接受 —— 内置 Ollama 也有 1536 维的模型（mxbai-embed-large）。

**校验时机**：创建知识库时校验所选模型的 `embedding_dimension`；未知（NULL）时**拒绝**，提示先在模型管理里确认维度 —— 宁可不建，也不要建出一个必然写不进向量的库。

### 2.5 文档原文存哪 —— **`kb_document` 加一列 `content`**

**决定**：`kb_document` 新增 `content MEDIUMTEXT NULL` 存文档原文（一期仅 TXT）。索引成功后**保留**（重新索引、导出、排查都要用）。

**为什么**：`vec_chunk.embedding` 是 `NOT NULL` —— 没有向量就写不进 PG。分段正文与向量必须同时落库，所以原文必须先有个落脚点，而 `kb_document` 里没有这一列。

**替代方案**：
- 存本地文件系统 —— 生产是多副本无状态部署（K8s），文件不共享，重启即丢。**已否决。**
- PG 侧再开一张原文表 —— PG 侧只该放分段正文与向量；且 `schema-pg.sql` 是向量库，混入业务原文会让双库的职责边界更糊。**已否决。**

**代价**：MySQL 行变大。列表查询用 `select` 指定列，不取 `content`。

### 2.6 分块 —— **按字符固定切分**

**决定**：按字符数固定切分，块大小与重叠取自 `kb_knowledge_base.chunk_size` / `chunk_overlap`（默认 500 / 50）。

**为什么**：CLAUDE.md §1.2 明确「固定分块」。

**替代方案**：按段落 / 句子边界切（体验更好，但要处理超长段落、无标点文本等边界情况）—— 一期不做。

**代价**：可能把句子切碎，检索质量打折。这是「降级」定位的既定代价。

### 2.7 删除顺序 —— **MySQL 软删 → 异步删 PG → MySQL 硬删**

**决定**：删除文档时先把 `kb_document` 置 `deleted=1` 且 `status=deleting`，异步任务删掉 PG 里的 chunk，再物理删除 MySQL 行。

**为什么**：CLAUDE.md §3.3 明确这个顺序 —— 反过来会在中途崩溃时导致**检索静默返回空**（PG 已删、MySQL 还是 ready，检索时 chunk 查不到但文档显示已就绪）。

### 2.8 跨库写入可重入 —— **PG 侧 upsert 幂等 + MySQL 侧状态机

**决定**：`vec_chunk` 用 `ON CONFLICT (doc_id, chunk_index) DO UPDATE`（唯一键 `uk_vec_chunk_doc_idx` 保证幂等）；MySQL 侧用 `uploaded / indexing / ready / failed / deleting` 状态机；定时任务兜底重跑卡在 `indexing` 的文档与未完成的 `deleting`。

**为什么**：两库之间没有分布式事务（CLAUDE.md §3.3）。任何一次写一半的崩溃都必须能安全重跑。

---

## 三、数据模型

### 3.1 `kb_document` 新增一列

```sql
ALTER TABLE `kb_document`
  ADD COLUMN `content` MEDIUMTEXT NULL
    COMMENT '文档原文，一期仅 TXT；索引完成后保留，供重新索引与排查';
```

### 3.2 `kb_knowledge_base` 只改注释

```sql
ALTER TABLE `kb_knowledge_base`
  MODIFY COLUMN `embedding_model` VARCHAR(128) NULL
    COMMENT '向量化用的模型 ai_model.public_id；跨接入点唯一，不能只存上游模型名';
```

列类型与宽度不变，**数据无损**。

### 3.3 不动的部分

`kb_knowledge_base` 的 `uk_name`（覆盖软删行 → 服务层校验时报错说清楚）、`kb_document` 的 `uk_public_id` / `idx_kb_status` / `idx_kb_created` / 外键 `fk_kb_document_kb` 全部保留。

`vec_chunk` 不动 —— PG 侧本来就没有 migration 机制（维度定死，只能重建）。

### 3.4 跨库归属

| 数据 | 位置 | 关联 |
|---|---|---|
| 知识库配置 | MySQL `kb_knowledge_base` | — |
| 文档元数据 + 状态机 + 原文 | MySQL `kb_document` | `kb_id`（同库，有外键） |
| 分段正文 + 向量 | PG `vec_chunk` | `kb_id` / `doc_id`（跨库，逻辑引用，无外键） |

---

## 四、模块边界

### 4.1 依赖方向

```
hify-shared ← hify-ai ← hify-knowledge
```

- `hify-knowledge` 依赖 `shared` 与 `ai`（embedding 调用），**不得反向**：ai 里不能出现 knowledge 的类型（否则 `ai ↔ knowledge` 成环，CLAUDE.md §2.1 四条不变量之一）。
- `hify-knowledge` **不得依赖** conversation / workflow（编排层）。
- PG 侧访问一律用 `NamedParameterJdbcTemplate` 手写 SQL，**不用 MP 的 `BaseMapper`**（pgvector 的 `<=>` 与 HNSW 查询 MP 生成不了）。

### 4.2 `api` 包的公开契约

| 类型 | 作用 |
|---|---|
| `KnowledgeBaseService` | 知识库 CRUD |
| `KnowledgeDocumentService` | 文档上传、列表、删除、重新索引 |
| `KnowledgeSearchService` | 向量检索 |
| `KnowledgeBaseSummaryResp` / `KnowledgeBaseDetailResp` | 知识库响应 |
| `DocumentSummaryResp` / `DocumentDetailResp` | 文档响应 |
| `KnowledgeBaseCreateReq` / `KnowledgeBaseUpdateReq` | 知识库请求 |
| `DocumentUploadReq`（multipart） | 文档上传 |
| `SearchQuery` / `SearchResult` | 检索请求与结果 |
| `KnowledgeBaseListQuery` / `KnowledgeBaseListResp` | 列表（游标分页） |
| `DocumentListQuery` / `DocumentListResp` | 文档列表（游标分页） |

### 4.3 分层落位

| 层 | 内容 |
|---|---|
| `api` | 上表全部 |
| `internal/entity` | `KnowledgeBaseDO`、`DocumentDO`（都继承 `BaseEntity`） |
| `internal/mapper` | `KnowledgeBaseMapper`、`DocumentMapper`（MP，MySQL 侧） |
| `internal/repository` | `VecChunkRepository`（PG 侧，`NamedParameterJdbcTemplate`） |
| `internal/converter` | `KnowledgeConverter` |
| `internal/service` | `KnowledgeBaseServiceImpl`、`DocumentServiceImpl`、`IndexingService`、`SearchService`、`TextChunker` |
| `internal/controller` | `KnowledgeBaseController`、`DocumentController` |

### 4.4 状态机

```
uploaded ──→ indexing ──→ ready
                │
                └──→ failed ──(重新索引)──→ indexing

任意状态 ──删除──→ deleting ──(PG 清完)──→ (MySQL 硬删)
```

- `uploaded`：原文已落库，还没提交索引
- `indexing`：正在分块 + 向量化 + 写 PG
- `ready`：可检索
- `failed`：索引失败，`error_message` 记原因
- `deleting`：软删已置位，等 PG 清完

---

## 五、实现要点

### 5.1 索引流程

```
上传 TXT → 校验（类型、大小、编码）→ 落 kb_document(content, status=uploaded)
        → 提交异步任务 → status=indexing
        → 分块（按字符）→ 调 ai 的 embedding → 写 PG（upsert 幂等）
        → status=ready, chunk_count, indexed_at
        失败 → status=failed, error_message
```

**分块与向量的批次**：一次向量化一批（如 32 段），避免一次几千条把请求体撑爆。

### 5.2 检索流程

```
query → 调 ai 的 embedding（同一 embedding 模型）→ PG HNSW 余弦检索 top-k
      → 返回 chunk 内容 + 相似度 + 引用信息（doc 名称、chunk_index）
```

**过滤**：按 `kb_id` 过滤（`idx_vec_chunk_kb`）。一期不做 metadata 过滤。

### 5.3 删除流程

```
DELETE /documents/{id}
  → MySQL: deleted=1, status=deleting
  → 异步: DELETE FROM vec_chunk WHERE doc_id = ?
  → MySQL: 物理 DELETE 该行
```

**删除知识库**：有文档时拦截（同 Provider 的模式），报 409。

### 5.4 兜底定时任务

每 5 分钟扫一次：
- `status='indexing'` 且 `updated_at` 超过 10 分钟 → 重新索引
- `status='deleting'` → 继续删 PG，完成后硬删 MySQL 行

多副本下用 Redis 分布式锁去重（与 Provider 健康检查、tool 目录同步同一套）。

### 5.5 前端

| 项 | 落位 |
|---|---|
| 路由 | `/knowledge` 已存在（占位页），换成真实页面 |
| 菜单 | `App.vue` 的 `menus` 加「知识库」 |
| 页面 | `hify-web/src/views/knowledge/KnowledgeListView.vue`（列表）+ `KnowledgeDetailView.vue`（文档管理，路由 `/knowledge/:id`） |
| 类型 | `src/types/knowledge.ts` |
| API | `src/api/knowledge.ts` |
| 分页 | 游标分页（`cursor` / `size` → `items` / `hasNext` / `nextCursor`） |

**上传**：`el-upload` 限定 `.txt`，用 `http-request` 自定义上传（走 `utils/request.ts` 的 axios 实例，保留鉴权头与错误提示）。

---

## 六、验证方式

| 阶段 | 验证 |
|---|---|
| 数据模型 | `mvn -pl hify-app -am test -Dtest=AiSchemaMappingTest`；迁移脚本执行前后核对行数 |
| Entity + Mapper | `mvn -pl hify-knowledge -am test`（映射测试：表名、列、主键、逻辑删除） |
| DTO | `mvn -pl hify-knowledge -am compile` + 校验注解单测 |
| Service | Mockito mock Mapper，断言状态机流转与错误码 |
| PG 侧 | 用 Testcontainers 或本地 PG 跑 upsert 幂等与检索；一期至少用手写 SQL 单测覆盖 SQL 拼装 |
| Controller | `mvn verify` + `curl` 打真实接口 |
| 前端 | `cd hify-web && npm test && npm run type-check && npm run build` |
| 完整验收 | curl 逐接口 + 浏览器全流程；测试数据删净 |

---

## 七、注意事项

### 双库

- **两库之间没有分布式事务。** 任何写一半的崩溃都必须能安全重跑：PG 侧 upsert 幂等 + MySQL 侧状态机 + 定时任务兜底。
- **删除顺序固定「MySQL 软删 → 异步删 PG → MySQL 硬删」。** 反过来会让检索静默返回空。
- **PG 侧不用 MP 的 BaseMapper。** pgvector 的 `<=>` 运算符与 HNSW 查询 MP 生成不了，一律 `NamedParameterJdbcTemplate` 手写 SQL。
- **向量维度建表时定死。** 换维度必须重建 `vec_chunk`，PG 不支持 ALTER。

### 依赖

- **ai ↔ knowledge 不得成环。** embedding 调用在 `hify-ai` 的 `api` 包里，knowledge 调它；ai 里不许出现 knowledge 的类型。
- **`hify-ai` 是纯能力层** —— 只做「给我文本，返回向量」，不做「检索」「拼上下文」这类组合逻辑。

### 阻塞与线程

- **embedding 调用是阻塞的**（要等上游返回），必须提交到 `llmExecutor`，不占 Tomcat 线程。
- **索引任务整体是慢能力**（分块 + 多次向量化 + 写 PG），提交到 `asyncExecutor`，不要在 HTTP 线程里同步做。

### 验证纪律

- **每层验证通过再进下一层。**
- **不要把验收数据留在库里**；删完核对行数。

---

## 八、实现期发现（都是跑起来才暴露的，单测与编译都发现不了）

### 8.1 索引任务必须在事务提交后才提交

**症状**：文档上传后永远停在 `uploaded`，PG 里一条分段都没有，日志里连一行「索引失败」都没有。

**原因**：`upload()` 是 `@Transactional` 的，方法体里直接调 `indexingService.submit(id)`。异步任务在事务提交前就起来回查文档行 —— 那一行还没提交，查不到，任务静默返回。

**改法**：发事件，监听器用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 动作。

```java
events.publishEvent(new DocumentUploadedEvent(entity.getId()));   // 事务里只发事件
// 监听器
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onDocumentUploaded(DocumentUploadedEvent event) { indexingService.submit(event.documentId()); }
```

**推广**：任何「事务里写库、事务外回查这行再干活」的异步任务，都要这么写。删除流程的清理任务同理。

### 8.2 兜底任务要同时捞 `uploaded` 与 `indexing`

只捞 `indexing` 的话，任务根本没被提交（丢消息、进程被杀）的文档会永远停在 `uploaded` —— 症状是「文档传上去了，但检索不到任何内容」，而且没有任何错误日志。

### 8.3 PG 数据源不能作为 `DataSource` bean 暴露

**症状**：应用启动时 MyBatis-Plus 连到了 PG，报「表不存在」。

**原因**：Spring Boot 的 `DataSourceAutoConfiguration` 带 `@ConditionalOnMissingBean(DataSource.class)` —— 容器里只要出现**任何** `DataSource` bean，它就不再生成 MySQL 的数据源，MyBatis-Plus 拿到的是唯一的那个（PG）。

**改法**：用一个包装类型 `VectorDataSource` 持有 Hikari 连接池，对外只暴露 `NamedParameterJdbcTemplate`。它的 bean 类型不是 `DataSource`，MySQL 侧照旧由 Boot 自动配置接管。

### 8.4 `TextChunker` 这类无状态工具类也要注册成 bean

纯 POJO 不会被 Spring 管理，构造器注入直接失败（`No qualifying bean of type 'TextChunker'`）。要么标 `@Component`，要么在配置里 `@Bean`。

### 8.5 文件类型只看扩展名，不看 MIME

浏览器对 `.txt` 常发 `application/octet-stream`，对 `.pdf` 也可能发 `text/plain` —— 拿 MIME 当判据会放进二进制文件。扩展名是用户看得见、也真正决定格式的那个。

### 8.6 上游目录接口不标模型用途

`GET /v1/models` 只给模型 id，不给「这是 chat 还是 embedding」。所以：

- 同步下来的模型一律先按 `chat` 落
- 用途与向量维度**由用户在管理台人工认定**
- 知识库建库时只接受「已启用 + 类型是 embedding + 维度是 1536」的模型

这条也解释了为什么 `hify-ai` 必须补模型管理：没有模型行，就没有可认定的 embedding 模型。

### 8.7 补了两处列

| 表 | 列 | 原因 |
|---|---|---|
| `kb_document` | `content MEDIUMTEXT` | 没有原文，向量算不出来、分段正文也无处可放（`vec_chunk.embedding` 是 NOT NULL） |
| `kb_knowledge_base` | `config_version` | 名称全局唯一，两个管理员同时改名会互相覆盖 |
