-- Hify 向量库建表脚本（PostgreSQL 16 + pgvector）
--
-- 与 MySQL 业务库是两套独立数据，靠 kb_id / doc_id 逻辑关联，跨库没有外键。
-- MySQL 侧的建表脚本在同目录的 schema.sql。
--
-- 执行
--   docker compose exec -T postgres psql -U hify -d hify \
--     -v ON_ERROR_STOP=1 < hify-app/src/main/resources/db/schema-pg.sql
--
-- ⚠️ embedding 的维度（1536）建表时定死，换维度不同的 embedding 模型
--    必须重建这张表——PG 不支持 ALTER 改 vector 维度。
--
-- ⚠️ 需要先装扩展（compose 的 pg-init 脚本已做，手工部署时需自行执行）：
--    CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE vec_chunk (
  id          BIGSERIAL    PRIMARY KEY,
  kb_id       BIGINT       NOT NULL,
  doc_id      BIGINT       NOT NULL,
  chunk_index INT          NOT NULL,
  content     TEXT         NOT NULL,
  embedding   vector(1536) NOT NULL,
  metadata    JSONB,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uk_vec_chunk_doc_idx UNIQUE (doc_id, chunk_index)
);

-- 按知识库过滤（删除与列表用）
CREATE INDEX idx_vec_chunk_kb ON vec_chunk (kb_id);

-- 余弦距离 <-> 必须配 vector_cosine_ops；m 与 ef_construction 建索引时定死，改动需重建
CREATE INDEX idx_vec_chunk_embedding_hnsw ON vec_chunk
  USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
