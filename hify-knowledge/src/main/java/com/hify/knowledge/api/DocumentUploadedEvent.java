package com.hify.knowledge.api;

/**
 * 文档已落库、可以开始索引的事件。
 *
 * <p>在事务提交后触发 —— 索引任务会立刻回查文档行，事务没提交时那一行还看不见，
 * 任务会扑空。
 *
 * @param documentId 文档主键
 */
public record DocumentUploadedEvent(Long documentId) {
}
