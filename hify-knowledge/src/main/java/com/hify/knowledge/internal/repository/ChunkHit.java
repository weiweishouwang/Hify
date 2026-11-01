package com.hify.knowledge.internal.repository;

/**
 * 一条检索命中的分段。
 *
 * @param id         分段在 PG 里的主键
 * @param docId      文档在 MySQL 里的主键
 * @param chunkIndex 分段序号
 * @param content    分段正文
 * @param score      相似度，1 减余弦距离，越大越相近
 */
public record ChunkHit(long id, long docId, int chunkIndex, String content, double score) {
}
