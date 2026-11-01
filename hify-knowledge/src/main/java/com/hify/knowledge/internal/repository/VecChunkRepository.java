package com.hify.knowledge.internal.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;

/**
 * PG 侧的分段与向量存取。
 *
 * <p><b>一律手写 SQL</b> —— pgvector 的 {@code <=>} 运算符与 HNSW 查询
 * MyBatis-Plus 生成不了（CLAUDE.md §3.3）。
 *
 * <p>向量以 {@code [0.1,0.2]} 的字面量形式传参，SQL 侧用
 * {@code CAST(:embedding AS vector)} 转成 pgvector 的类型。
 */
@Repository
public class VecChunkRepository {

    /**
     * 幂等写入分段。
     *
     * <p>靠唯一键 {@code uk_vec_chunk_doc_idx (doc_id, chunk_index)} 做
     * {@code ON CONFLICT DO UPDATE} —— 重新索引时重复执行不会产生重复行，
     * 这是跨库写入「可重入」的基础（CLAUDE.md §3.3）。
     */
    private static final String UPSERT_SQL = """
            INSERT INTO vec_chunk (kb_id, doc_id, chunk_index, content, embedding)
            VALUES (:kbId, :docId, :chunkIndex, :content, CAST(:embedding AS vector))
            ON CONFLICT (doc_id, chunk_index) DO UPDATE
            SET content = EXCLUDED.content, embedding = EXCLUDED.embedding
            """;

    /** 删除某个文档的全部分段。 */
    private static final String DELETE_BY_DOC_SQL = "DELETE FROM vec_chunk WHERE doc_id = :docId";

    /** 统计某个文档的分段数。 */
    private static final String COUNT_BY_DOC_SQL = "SELECT COUNT(*) FROM vec_chunk WHERE doc_id = :docId";

    /**
     * 向量检索。
     *
     * <p>{@code <=>} 是余弦距离；{@code 1 - 距离} 就是余弦相似度。
     * HNSW 索引配的是 {@code vector_cosine_ops}，与这里的运算符对得上。
     */
    private static final String SEARCH_SQL = """
            SELECT id, doc_id, chunk_index, content,
                   (1 - (embedding <=> CAST(:embedding AS vector))) AS score
            FROM vec_chunk
            WHERE kb_id = :kbId
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :topK
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public VecChunkRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 幂等写入一批分段。
     *
     * @param kbId   知识库主键
     * @param docId  文档主键
     * @param chunks 分段
     */
    public void upsertChunks(Long kbId, Long docId, List<ChunkRecord> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = new MapSqlParameterSource[chunks.size()];
        for (int index = 0; index < chunks.size(); index++) {
            ChunkRecord chunk = chunks.get(index);
            batch[index] = new MapSqlParameterSource()
                    .addValue("kbId", kbId)
                    .addValue("docId", docId)
                    .addValue("chunkIndex", chunk.index())
                    .addValue("content", chunk.content())
                    .addValue("embedding", toVectorLiteral(chunk.embedding()));
        }
        jdbc.batchUpdate(UPSERT_SQL, batch);
    }

    /**
     * 删除某个文档的全部分段。
     *
     * @param docId 文档主键
     * @return 删除的行数
     */
    public int deleteByDocId(Long docId) {
        return jdbc.update(DELETE_BY_DOC_SQL, new MapSqlParameterSource("docId", docId));
    }

    /**
     * 统计某个文档的分段数。
     *
     * @param docId 文档主键
     * @return 分段数
     */
    public long countByDocId(Long docId) {
        Long count = jdbc.queryForObject(COUNT_BY_DOC_SQL, new MapSqlParameterSource("docId", docId), Long.class);
        return count == null ? 0L : count;
    }

    /**
     * 在知识库里做向量检索。
     *
     * @param kbId 知识库主键
     * @param query 查询向量
     * @param topK  返回条数
     * @return 命中结果，按相似度从高到低
     */
    public List<ChunkHit> search(Long kbId, float[] query, int topK) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("kbId", kbId)
                .addValue("embedding", toVectorLiteral(query))
                .addValue("topK", topK);
        return jdbc.query(SEARCH_SQL, params, (rs, rowNum) -> new ChunkHit(
                rs.getLong("id"),
                rs.getLong("doc_id"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getDouble("score")));
    }

    /** 把 float[] 转成 pgvector 的字面量形式 {@code [0.1,0.2]}。 */
    static String toVectorLiteral(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("向量不能为空");
        }
        StringBuilder literal = new StringBuilder(vector.length * 8 + 2);
        literal.append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(String.format(Locale.ROOT, "%s", vector[index]));
        }
        return literal.append(']').toString();
    }
}
