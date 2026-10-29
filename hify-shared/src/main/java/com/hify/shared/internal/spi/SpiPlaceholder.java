/**
 * shared 模块 · 共享内核 —— 异常体系、错误码、当前用户上下文、trace id、平台域的用户与密钥。
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
 * <p>当前用途：本模块暂无 SPI 实现。</p>
 *
 * <p>本文件是占位类，说明本包的职责与约束；写实际代码时替换掉它。</p>
 */
public class SpiPlaceholder {
}
