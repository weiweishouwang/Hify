package com.hify.knowledge.internal.repository;

/**
 * 一条待写入 PG 的分段。
 *
 * @param index     分段序号，从 0 开始
 * @param content   分段正文
 * @param embedding 该分段的向量
 */
public record ChunkRecord(int index, String content, float[] embedding) {
}
