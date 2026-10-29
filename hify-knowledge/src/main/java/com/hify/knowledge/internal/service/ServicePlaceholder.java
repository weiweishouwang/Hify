/**
 * knowledge 模块 · 知识库 —— TXT 上传、固定分块、embedding、向量检索。
 *
 * <p>业务逻辑与事务。
 *
 * <p>硬约束（CLAUDE.md §3.2）：
 * <ul>
 *   <li>@Transactional 只出现在这一层</li>
 *   <li>编排其他模块时只调用对方的 api 包接口</li>
 *   <li>禁止直接访问其他模块的 Mapper 或表</li>
 *   <li>跨模块副作用通过 Spring 事件解耦</li>
 *   <li>事务方法内禁止调用模型、MCP、工具执行等慢能力——它们会长时间持有数据库连接</li>
 * </ul>
 *
 * <p>这是模块的业务核心，也是唯一允许出现事务边界的地方。</p>
 *
 * <p>本文件是占位类，说明本包的职责与约束；写实际代码时替换掉它。</p>
 */
public class ServicePlaceholder {
}
