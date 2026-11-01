package com.hify.knowledge.api;

/**
 * 文档已软删、可以清理 PG 的事件。
 *
 * <p>同样在事务提交后触发：清理任务要先看到软删标记，再删 PG 的分段，最后硬删 MySQL 行。
 *
 * @param documentId 文档主键
 * @param kbId       所属知识库主键，用于刷新冗余文档数
 */
public record DocumentRemovedEvent(Long documentId, Long kbId) {
}
