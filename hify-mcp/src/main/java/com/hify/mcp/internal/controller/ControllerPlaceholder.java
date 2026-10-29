/**
 * mcp 模块 · MCP 接入 —— MCP client、server 配置管理、MCP 来源的 ToolProvider。
 *
 * <p>HTTP 接入层。
 *
 * <p>硬约束（CLAUDE.md §3.2）：
 * <ul>
 *   <li>方法体 3–8 行，只做参数绑定、调 service、返回 DTO</li>
 *   <li>禁止业务逻辑</li>
 *   <li>禁止 @Transactional</li>
 *   <li>禁止直接注入 Mapper</li>
 *   <li>禁止返回数据对象（DO）</li>
 * </ul>
 *
 * <p>控制器是薄壳：任何需要判断与分支的逻辑都属于 service 层。</p>
 *
 * <p>本文件是占位类，说明本包的职责与约束；写实际代码时替换掉它。</p>
 */
public class ControllerPlaceholder {
}
