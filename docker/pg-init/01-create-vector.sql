-- 首次初始化 PostgreSQL 时执行（仅在数据卷为空时跑一次）。
--
-- pgvector 扩展：知识库的向量检索靠它。扩展属于数据库而不是某个 schema，
-- 装在 public 下，hify-knowledge 的 JdbcTemplate 直接用。
CREATE EXTENSION IF NOT EXISTS vector;
