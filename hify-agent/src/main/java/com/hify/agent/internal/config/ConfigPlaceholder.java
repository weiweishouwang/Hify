/**
 * agent 模块 · Agent —— Agent 配置的存取与组装（不执行推理、不调用工具）。
 *
 * <p>模块级 Spring 配置。
 *
 * <p>硬约束（CLAUDE.md §3.2）：
 * <ul>
 *   <li>只装配本模块的 bean</li>
 *   <li>禁止在这里放业务逻辑</li>
 *   <li>禁止跨模块装配对方的内部类</li>
 * </ul>
 *
 * <p>模块要拆成独立服务时，本包是装配的起点。</p>
 *
 * <p>本文件是占位类，说明本包的职责与约束；写实际代码时替换掉它。</p>
 */
public class ConfigPlaceholder {
}
