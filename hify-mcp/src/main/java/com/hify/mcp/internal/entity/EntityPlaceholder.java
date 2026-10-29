/**
 * mcp 模块 · MCP 接入 —— MCP client、server 配置管理、MCP 来源的 ToolProvider。
 *
 * <p>数据库映射对象。
 *
 * <p>硬约束（CLAUDE.md §3.2）：
 * <ul>
 *   <li>只在 internal 内使用</li>
 *   <li>禁止出现在 api 包</li>
 *   <li>禁止作为 controller 的返回类型</li>
 *   <li>禁止作为跨模块方法签名</li>
 *   <li>一般继承 shared 的 BaseDO；事件流表（分区表）不继承</li>
 * </ul>
 *
 * <p>DO 携带 ORM 注解，跨模块传它会在序列化和将来拆服务时出问题。</p>
 *
 * <p>本文件是占位类，说明本包的职责与约束；写实际代码时替换掉它。</p>
 */
public class EntityPlaceholder {
}
