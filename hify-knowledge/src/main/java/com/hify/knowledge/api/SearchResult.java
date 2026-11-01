package com.hify.knowledge.api;

/**
 * 一条检索结果。
 *
 * @param chunkId    分段在 PG 里的主键
 * @param docId      文档在 MySQL 里的主键
 * @param docName    文档名，便于溯源
 * @param chunkIndex 分段序号
 * @param content    分段正文
 * @param score      相似度，余弦距离换算成 1 减距离，越大越相近
 */
public record SearchResult(
        Long chunkId,
        Long docId,
        String docName,
        Integer chunkIndex,
        String content,
        Double score) {
}
