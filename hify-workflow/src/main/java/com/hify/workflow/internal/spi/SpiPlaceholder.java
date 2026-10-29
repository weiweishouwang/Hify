/**
 * workflow 模块 · 工作流 —— 工作流定义的存取与执行（6 种节点）。
 *
 * <p>本模块对他人 SPI 的实现。
 *
 * <p>硬约束（CLAUDE.md §3.2）：
 * <ul>
 *   <li>SPI 接口本身放在消费方的 api 包（依赖倒置）——本包只放实现</li>
 *   <li>禁止反向依赖消费方的 internal</li>
 *   <li>实现必须可被 Spring 容器收集（如实现 List<ToolProvider> 的聚合）</li>
 * </ul>
 *
 * <p>当前用途：本模块对外暴露工作流执行入口。</p>
 *
 * <p>本文件是占位类，说明本包的职责与约束；写实际代码时替换掉它。</p>
 */
public class SpiPlaceholder {
}
