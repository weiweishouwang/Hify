-- Hify 本地开发库：知识库增量迁移（文档原文列 + embedding_model 语义澄清）
-- 日期：2026-11-01
--
-- 手动执行脚本，不由应用启动或 Flyway 自动运行。
-- 已检查的目标：本地 hify-mysql，MySQL 8.4.13，数据库 hify。
-- 背景：2026-11-01-hify-knowledge-module-design.md 决定知识库落 MySQL、
--        分段正文与向量落 PG，双库分工明确。
--
-- 无损边界：
--   1. 只 ALTER 补一列、改一列的 COMMENT；不删除任何已有表、列或记录。
--   2. 新增的 content 列默认为 NULL，已有行不受影响。
--   3. embedding_model 只改注释，类型与宽度不变，已有取值原样保留。
--      —— 语义从「上游模型标识」变为「ai_model.public_id」是**约定变更**，
--         不是数据迁移：已有库目前没有知识库数据，无需回填。
--   4. MySQL DDL 隐式提交，不能依赖 ROLLBACK 撤销；执行前必须备份。
--   5. 此脚本针对已检查的旧结构，仅执行一次，重跑前检查列是否已存在。
--
-- PG 侧不需要迁移：vec_chunk 的结构与维度（1536）都不变。

-- 等待元数据锁最多10秒，避免被长事务无限阻塞。
SET SESSION lock_wait_timeout = 10;

ALTER TABLE `kb_document`
  ADD COLUMN `content` MEDIUMTEXT NULL
    COMMENT '文档原文，一期仅 TXT；索引完成后保留，供重新索引与排查'
    AFTER `content_hash`;

ALTER TABLE `kb_knowledge_base`
  MODIFY COLUMN `embedding_model` VARCHAR(128) NULL
    COMMENT '向量化用的模型 ai_model.public_id；跨接入点唯一，不能只存上游模型名';
