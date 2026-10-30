# Hify 数据库层性能规范

> 适用范围：MySQL 8.x（业务库）+ PostgreSQL 16 + pgvector（向量库）
> 目标：让 AI 或人按本文建表时，无需再做设计判断，直接照抄。
> 配套：`docs/superpowers/specs/2026-10-29-hify-code-organization.md`（模块归属与表前缀）、`CLAUDE.md`（关键约束）

---

## 0. 三条硬约束（先看这个）

1. **同模块内建外键，跨模块只存 id 不建外键。** 事件流表（见 §2.2）因分区要求，连同模块内的外键也不能建。
2. **MySQL 不支持分区表上的外键**——分区表既不能定义外键，也不能被外键引用。
3. **MySQL 分区表要求分区表达式涉及的列必须包含在每一个 UNIQUE 键（含主键）中。** 因此分区表的主键必须是 `(id, created_at)` 联合主键，且**不能有独立的 `public_id` 唯一索引**——这直接决定了哪些表能分区。

---

## 1. 通用字段约定

### 1.1 建表模板 —— 实体表（有对外暴露路径、不分区）

```sql
CREATE TABLE `<table_name>` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `public_id`  CHAR(26)        NOT NULL                             COMMENT '对外 ID，ULID(Crockford Base32)',
  -- ↓ 业务字段 ↓
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  `created_by` BIGINT UNSIGNED NULL                                 COMMENT '创建者 user.id',
  `deleted_at` DATETIME(3)     NULL                                 COMMENT '软删时间，NULL 表示未删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_id` (`public_id`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='<表说明>';
```

### 1.2 建表模板 —— 事件流表（增长快、分区、无 public_id）

```sql
CREATE TABLE `<table_name>` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  -- ↓ 业务字段 ↓
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`, `created_at`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='<表说明>'
PARTITION BY RANGE COLUMNS(`created_at`) (
  PARTITION `p202601` VALUES LESS THAN ('2026-02-01'),
  PARTITION `p202602` VALUES LESS THAN ('2026-03-01'),
  PARTITION `p202603` VALUES LESS THAN ('2026-04-01'),
  PARTITION `pmax`    VALUES LESS THAN (MAXVALUE)
);
```

> 模板里的分区是示例，实际建表时**按月建分区 + 一个 `pmax` 兜底分区**。`pmax` 必须存在，否则超出范围的写入会直接失败。

### 1.3 字段类型选型（照此执行，不要自选）

| 场景 | 类型 | 禁止 |
|---|---|---|
| 主键 | `BIGINT UNSIGNED AUTO_INCREMENT` | `INT`（4B 上限，5 年就能到）、UUID 做聚簇主键 |
| 对外 ID | `CHAR(26)`（ULID） | `CHAR(36)` 带连字符的 UUID（多占 10 字节且不可排序） |
| 时间 | `DATETIME(3)` | `TIMESTAMP`（2038 上限 + 时区隐式转换 + 默认秒精度） |
| 金额/精确小数 | `DECIMAL(18,4)` | `FLOAT` / `DOUBLE`（二进制浮点无法精确表示小数） |
| 计数 | `INT UNSIGNED` / `BIGINT UNSIGNED` | 有符号 INT（负数无意义） |
| 短字符串 | `VARCHAR(n)` | `CHAR(n)`（会补尾随空格，比较时易出错） |
| 长文本 | `TEXT` / `MEDIUMTEXT` | `VARCHAR(65535)`（受行大小限制） |
| 结构化配置 | `JSON`（MySQL 8 原生） | `TEXT` 存 JSON 字符串（无法建函数索引/多值索引） |
| 布尔 | `TINYINT(1)` | MySQL 无 BOOLEAN 类型 |
| 状态/类型枚举 | `VARCHAR(32)` + 应用层常量 | MySQL `ENUM`（改值要 DDL、排序按定义顺序而非字典序） |
| ID 引用 | `BIGINT UNSIGNED` | `VARCHAR`（索引长度翻倍且类型不一致会隐式转换） |

### 1.4 通用规则

- **NOT NULL 优先**：所有字段默认 `NOT NULL`，只有"确实可能没有"才允许 `NULL`。`NULL` 影响 `COUNT(col)`、排序（NULL 排最前）、部分索引与比较语义
- **默认值**：字符串给 `''` 不给 NULL；数值给 `0`；时间给 `NULL`
- **所有时间存 UTC**，应用层负责转换。API 输出统一带时区偏移
- **字符集**：`utf8mb4` + `utf8mb4_0900_ai_ci`（MySQL 8 默认）
- **引擎**：InnoDB
- **禁止外键级联删除**（`ON DELETE CASCADE`）——它把一次小删除变成隐式全表操作，性能不可控且难以排查。删子表由应用层显式处理
- **禁止存储过程与触发器**——MyBatis-Plus 场景下无法调试，且执行计划不可见
- **禁止 `SELECT *`**——避免把 TEXT/JSON 大字段带出；MyBatis-Plus 也要显式列字段
- **索引列总长度 ≤ 3072 字节**（InnoDB DYNAMIC 行格式上限）。utf8mb4 下 VARCHAR(255) 占 1020 字节
- **单表二级索引 ≤ 5 个**——每个索引都是写放大

---

## 2. 索引设计原则

### 2.1 联合索引的列顺序

```
等值条件列  →  范围/排序列
```

例：`WHERE session_id = ? AND created_at > ? ORDER BY created_at`
索引：`idx_session_seq (session_id, created_at, id)`

**排序键必须是全序**——`ORDER BY created_at` 在同一毫秒有多行时顺序不确定，翻页会漂移，所以排序键后面要补 `id`。

### 2.2 必须建索引 / 禁止建索引

| 规则 | 说明 |
|---|---|
| **必须** | 主键；所有唯一键；所有 JOIN / 外键列；所有 `WHERE` 等值条件列；所有 `ORDER BY` 组的首列 |
| **禁止** | 单列选择性 < 10% 的列单独建索引（如 `status`、`type`、`deleted_at`）——它们应该只作为联合索引的一部分 |
| **禁止** | 与已有索引构成前缀重复的索引（`idx_a(a)` 与 `idx_ab(a,b)` 前者冗余） |
| **禁止** | 索引列上做函数运算或隐式类型转换——`WHERE DATE(created_at)=?` 应改为范围 `created_at >= ? AND created_at < ?`；varchar 列与数字比较会让索引失效 |

### 2.3 JSON 数组的多值索引（Hify 特有，必做）

`ai_agent.tool_ids` / `knowledge_ids` 存 JSON 数组，查询"哪些 Agent 用了工具 T"用 `JSON_CONTAINS`。**但 `JSON_CONTAINS` 不加索引就是全表扫**，必须建多值索引（MySQL 8.0.17+）：

```sql
ALTER TABLE `ai_agent`
  ADD INDEX `idx_tool_ids` ((CAST(`tool_ids`->'$[*]' AS CHAR(64) ARRAY))),
  ADD INDEX `idx_knowledge_ids` ((CAST(`knowledge_ids`->'$[*]' AS CHAR(64) ARRAY)));
```

对应查询（**可命中多值索引**）：

```sql
-- ✅ 值在左、JSON 数组在右 —— 走 idx_tool_ids
SELECT * FROM `ai_agent` WHERE 'tool_xxx' MEMBER OF (`tool_ids`->'$[*]');
```

> ⚠️ **操作数顺序不能反**。实测 MySQL 8.4.13：
> - `WHERE `tool_ids`->'$[*]' MEMBER OF (JSON_QUOTE('tool_xxx'))` → **全表扫**，索引完全用不上
> - `WHERE JSON_CONTAINS(`tool_ids`, JSON_QUOTE('tool_xxx'))` → 同样全表扫
> - `WHERE 'tool_xxx' MEMBER OF (`tool_ids`->'$[*]')` → `type: ref`，命中多值索引
>
> 预编译参数（`?` 绑定）同样能命中：优化器在执行期把参数当常量。

**注意**：多值索引要求被索引的 JSON 数组元素是标量。`tool_ids` 存的是 ID 字符串数组 ✅；若改成对象数组则无法建此索引。

### 2.4 分页查询必须走覆盖索引

深分页的优化依赖索引覆盖，**不要用 `SELECT *`**，否则每页都要回表。

---

## 3. 大表预判与应对

### 3.1 哪些表会变大（按增长速度）

| 表 | 增长来源 | 量级估算 | 触发分区的时间 |
|---|---|---|---|
| `conv_message` | 每次问答 ≥2 行 | 2000 人 × 50 轮/天 × 2 ≈ 20 万行/天 | **立即**（从建表起） |
| `conv_run` | 每次问答 1 行 | 10 万行/天 | **立即** |
| `conv_message_citation` | 每次引用 1-N 行 | 与 message 同量级 | **立即** |
| `wf_run` | 每次工作流运行 | 视使用量 | 十万行后 |
| `vec_chunk` | 知识库分块 | 文档量 × 分段数 | 十万行后 |
| `conv_session` | 每次新会话 1 行 | 2000 人 × 20 会话/天 ≈ 4 万行/天 | **不分区**（见下） |
| 其余配置表 | 几乎不增长 | < 10 万行 | 不分区 |

### 3.2 为什么 `conv_session` 不分区

分区要求"分区列必须在每个 UNIQUE 键中"。`conv_session` 有 `uk_public_id`（对外分享链接要用），若按 `created_at` 分区，唯一的 `uk_public_id` 就必须带上 `created_at`，唯一性从全局退化为"分区内唯一"——**外部链接的全局唯一性就没了**。

按量级（2000 人一年约 1500 万会话行）配上 `idx_user_created(user_id, created_at)` 完全够用，且能保留 FK 到 `user`。**结论：`conv_session` 与所有配置表不分区；只有事件流表分区。**

### 3.3 分区与归档策略

**分区粒度**：按月。MySQL 8.0 单表上限 8192 个分区，但实际建议 ≤ 100 个（每个分区都是独立的数据字典条目与文件）。月分区 3 年 = 36 个 ✅

**日常清理（不依赖分区也能做）**：

```sql
-- 分批删除，每批 5000 行，避免长事务与大量 undo
DELETE FROM `conv_message`
WHERE `created_at` < NOW(3) - INTERVAL 90 DAY
LIMIT 5000;
```

**分区滚动维护**（归档后删除分区，秒级）：

```sql
-- 1) 导出待归档分区
SELECT * FROM `conv_message` PARTITION (p202601) INTO OUTFILE '/backup/p202601.csv';
-- 2) 确认后删除整个分区（秒级，不产生大量 undo）
ALTER TABLE `conv_message` DROP PARTITION `p202601`;
-- 3) 新建同名分区承接后续数据
ALTER TABLE `conv_message` REORGANIZE PARTITION `pmax` INTO (
  PARTITION `p202601` VALUES LESS THAN ('2026-02-01'),
  PARTITION `pmax` VALUES LESS THAN (MAXVALUE)
);
```

**保留期**：对话消息 90 天热存储，180 天转归档；运行记录 30 天；引用记录随消息一起清。

### 3.4 vec_chunk 的分区（PG 侧）

```sql
CREATE TABLE `vec_chunk` (
  `id`          BIGSERIAL PRIMARY KEY,
  `kb_id`       VARCHAR(64)  NOT NULL,
  `doc_id`      VARCHAR(64)  NOT NULL,
  `chunk_index` INT          NOT NULL,
  `content`     TEXT         NOT NULL,
  `embedding`   vector(1536) NOT NULL,
  `metadata`    JSONB,
  `created_at`  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT `uk_vec_chunk_doc_idx` UNIQUE (`doc_id`, `chunk_index`)
);
```

**`UNIQUE (doc_id, chunk_index)` 是幂等写入的基础**——重新索引时靠它 upsert，冲突则覆盖，保证重跑安全。

**多知识库场景按 `kb_id` 做 LIST 分区**，每个分区独立 HNSW 索引，避免过滤条件拖垮检索：

```sql
CREATE TABLE `vec_chunk` (...) PARTITION BY LIST (`kb_id`)(...);
```

单知识库时不分区，靠 HNSW 索引即可。

### 3.5 PG 侧的索引

```sql
CREATE INDEX `idx_vec_chunk_embedding_hnsw` ON `vec_chunk`
  USING hnsw (`embedding` vector_cosine_ops) WITH (m = 16, ef_construction = 64);
```

- `<=>`（余弦距离）必须与 `vector_cosine_ops` 匹配；换运算符就换索引定义
- `m` 与 `ef_construction` **建索引时确定，改动需重建全部索引**——上线前用真实数据量基准测试再定值
- `ef_search` 是运行时查询参数（默认 40），做成可配置项

---

## 4. 分页查询规范

### 4.1 禁止 OFFSET 深分页

```sql
-- ❌ 禁止：OFFSET 1000000 时 MySQL 要扫描并丢弃 100 万行
SELECT * FROM `conv_message` WHERE `session_id` = ? ORDER BY `created_at` DESC LIMIT 20 OFFSET 1000000;

-- ✅ 游标分页（keyset pagination）：用上一页最后一行的排序键继续
SELECT * FROM `conv_message`
WHERE `session_id` = ?
  AND (`created_at`, `id`) < (?, ?)      -- 行值比较，命中 idx_session_seq
ORDER BY `created_at` DESC, `id` DESC
LIMIT 20;
```

**前提**：排序键必须唯一且稳定 → 排序键恒为 `(created_at, id)` 复合，WHERE 用**行值比较**（`(a,b) < (x,y)`），不能用 `a < x OR (a = x AND b < y)`（后者往往走不上索引）。

### 4.2 计数

```sql
-- ❌ 大表上 SELECT COUNT(*) 很贵
-- ✅ 用 LIMIT N+1 判断是否还有下一页
SELECT * FROM `conv_message` WHERE `session_id` = ? ORDER BY `created_at` DESC, `id` DESC LIMIT 21;
-- 返回 21 条 → 还有下一页；返回 ≤20 条 → 已到末页
```

管理台确实需要总数时：用 `information_schema.TABLES.TABLE_ROWS` 的估算值，或单独维护计数表。**禁止使用 `SQL_CALC_FOUND_ROWS`（已废弃）。**

### 4.3 其他硬性要求

- **分页必须带 `ORDER BY`**——不带则结果顺序不确定
- **单页上限**：默认 100，管理台最大 200。禁止 `LIMIT` 不带上限
- **不做页码跳转**——管理台列表用"上一页/下一页"游标，不要让用户跳到第 500 页
- **深分页的替代方案**：导出类需求走异步任务（生成文件后给下载链接），不要同步分页拉全量

---

## 5. Hify 各表的索引清单（建表时直接照抄）

### 5.1 MySQL — 实体表

| 表 | 主键 | 唯一索引 | 普通索引 |
|---|---|---|---|
| `user` | `id` | `uk_public_id`, `uk_external_id(external_id)` | `idx_username(username)` |
| `api_key` | `id` | `uk_key_hash(key_hash)`, `uk_prefix(prefix)` | `idx_user_status(user_id, status)`, `idx_expire_at(expire_at)` |
| `ai_model_provider` | `id` | `uk_public_id`, `uk_name(name)` | `idx_type_enabled(type, enabled)` |
| `ai_model` | `id` | `uk_public_id`, `uk_provider_model(provider_id, model_name)` | `idx_provider_id(provider_id)` |
| `ai_agent` | `id` | `uk_public_id`, `uk_name(name)` | `idx_model_id(model_id)`, `idx_status(status)`, **多值** `idx_tool_ids`, `idx_knowledge_ids` |
| `ai_agent_version` | `id` | `uk_agent_version(agent_id, version)` | `idx_agent_id(agent_id)` |
| `tool_definition` | `id` | `uk_public_id`, `uk_tool_key(source_type, tool_key)` | `idx_created_by(created_by)` |
| `mcp_server` | `id` | `uk_public_id`, `uk_name(name)` | `idx_enabled(enabled)` |
| `kb_knowledge_base` | `id` | `uk_public_id`, `uk_name(name)` | `idx_created_by(created_by)`, `idx_status(status)` |
| `kb_document` | `id` | `uk_public_id` | `idx_kb_status(kb_id, status)`, `idx_kb_created(kb_id, created_at)` |
| `conv_session` | `id` | `uk_public_id` | `idx_user_created(user_id, created_at)`, `idx_agent_id(agent_id)` |
| `wf_definition` | `id` | `uk_public_id`, `uk_name(name)` | `idx_created_by(created_by)`, `idx_status(status)` |

**同模块可建 FK 的**：`api_key.user_id → user.id`、`conv_session.user_id → user.id`、`kb_document.kb_id → kb_knowledge_base.id`、`ai_model.provider_id → ai_model_provider.id`、`ai_agent_version.agent_id → ai_agent.id`、`wf_run` 不建（分区表）。

**跨模块只存 id 不建 FK**：`ai_agent.model_id`、`conv_session.agent_id`、`conv_message.session_id` 等。

### 5.2 MySQL — 事件流表（分区、无 public_id、无 FK）

| 表 | 主键 | 索引 | 备注 |
|---|---|---|---|
| `conv_message` | `(id, created_at)` | `idx_session_seq(session_id, created_at, id)`, `idx_created_at(created_at)` | 分区键 `created_at` |
| `conv_run` | `(id, created_at)` | `idx_session_created(session_id, created_at)`, `idx_created_at(created_at)` | 分区键 `created_at` |
| `conv_message_citation` | `(id, created_at)` | `idx_message_id(message_id)`, `idx_chunk_id(chunk_id)` | 分区键 `created_at`；`session_id`/`message_id` 均为逻辑引用 |
| `wf_run` | `(id, created_at)` | `idx_definition_created(definition_id, created_at)`, `idx_created_at(created_at)` | 分区键 `created_at`；`definition_id` 逻辑引用 |

**`conv_message_citation` 的特殊约束**：它引用 `vec_chunk`（PG 侧，跨库），引用时**同时存三样**——`chunk_id`（精确指向）、`doc_id` + `chunk_index`（位置冗余，chunk 被重建后仍能找回）、**引用文本快照**（历史展示不依赖 PG 还在）。

---

## 6. 审计脚本（建表后跑一遍）

```sql
-- 6.1 查无索引的外键/关联列（应为 0 行或全部有索引覆盖）
SELECT c.TABLE_NAME, c.COLUMN_NAME, c.REFERENCED_TABLE_NAME
FROM information_schema.KEY_COLUMN_USAGE c
WHERE c.TABLE_SCHEMA = DATABASE()
  AND c.REFERENCED_TABLE_NAME IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS s
    WHERE s.TABLE_SCHEMA = c.TABLE_SCHEMA
      AND s.TABLE_NAME = c.TABLE_NAME
      AND s.COLUMN_NAME = c.COLUMN_NAME
      AND s.SEQ_IN_INDEX = 1
  );

-- 6.2 查冗余索引（前缀重复，应删除较短的那个）
SELECT a.TABLE_NAME, a.INDEX_NAME, b.INDEX_NAME
FROM information_schema.STATISTICS a
JOIN information_schema.STATISTICS b
  ON a.TABLE_SCHEMA = b.TABLE_SCHEMA AND a.TABLE_NAME = b.TABLE_NAME
 AND a.INDEX_NAME <> b.INDEX_NAME AND a.SEQ_IN_INDEX = 1 AND b.SEQ_IN_INDEX = 1
WHERE a.TABLE_SCHEMA = DATABASE()
  AND (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
       FROM information_schema.STATISTICS x
       WHERE x.TABLE_SCHEMA = a.TABLE_SCHEMA AND x.TABLE_NAME = a.TABLE_NAME
         AND x.INDEX_NAME = a.INDEX_NAME)
   LIKE CONCAT((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
                FROM information_schema.STATISTICS y
                WHERE y.TABLE_SCHEMA = b.TABLE_SCHEMA AND y.TABLE_NAME = b.TABLE_NAME
                  AND y.INDEX_NAME = b.INDEX_NAME), '%');

-- 6.3 查大表与索引体积（评估是否需要分区/归档）
SELECT TABLE_NAME, TABLE_ROWS,
       ROUND(DATA_LENGTH/1024/1024) AS data_mb,
       ROUND(INDEX_LENGTH/1024/1024) AS index_mb
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY DATA_LENGTH + INDEX_LENGTH DESC;

-- 6.4 确认分区表已建分区且有 pmax 兜底
SELECT TABLE_NAME, PARTITION_NAME, PARTITION_METHOD, PARTITION_EXPRESSION, PARTITION_DESCRIPTION
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = DATABASE() AND PARTITION_NAME IS NOT NULL
ORDER BY TABLE_NAME, PARTITION_ORDINAL_POSITION;
```

---

## 7. 建表后的验收清单

每张表建完，逐条确认：

- [ ] 引擎 `InnoDB`，字符集 `utf8mb4`，排序规则 `utf8mb4_0900_ai_ci`
- [ ] 所有时间字段是 `DATETIME(3)`，有 `DEFAULT CURRENT_TIMESTAMP(3)`，`updated_at` 有 `ON UPDATE CURRENT_TIMESTAMP(3)`
- [ ] 所有字段 NOT NULL，除明确允许为空的
- [ ] 没有 `FLOAT`/`DOUBLE` 存金额，没有 `TIMESTAMP`，没有 MySQL `ENUM`
- [ ] 对外暴露的表有 `public_id CHAR(26)` + `uk_public_id`
- [ ] 事件流表：主键是 `(id, created_at)`，有分区且有 `pmax`，**没有**独立 `uk_public_id`，**没有**外键
- [ ] 实体表：有 `uk_public_id`，同模块关联有 FK，跨模块关联只有 id 字段
- [ ] 每个 JOIN / WHERE 等值列 / ORDER BY 首列都有索引
- [ ] 无前缀冗余索引，单表二级索引 ≤ 5
- [ ] `ai_agent` 的 `tool_ids` / `knowledge_ids` 有多值索引
- [ ] `vec_chunk` 有 `UNIQUE(doc_id, chunk_index)` 和 HNSW 索引，运算符与索引定义匹配
- [ ] §6 的四个审计脚本跑过，结果符合预期
