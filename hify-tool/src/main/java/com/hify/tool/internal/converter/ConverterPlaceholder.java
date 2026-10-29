/**
 * tool 模块 · 工具 —— Tool 抽象 + ToolProvider SPI + 内置与 HTTP 两种来源。
 *
 * <p>DO 与 DTO 之间的转换。
 *
 * <p>硬约束（CLAUDE.md §3.2）：
 * <ul>
 *   <li>转换逻辑不得堆在 service 里</li>
 *   <li>DO → DTO 与 DTO → DO 各自独立，不要来回互转</li>
 * </ul>
 *
 * <p>转换集中在这里，service 才不会被 if-else 淹没。</p>
 *
 * <p>本文件是占位类，说明本包的职责与约束；写实际代码时替换掉它。</p>
 */
public class ConverterPlaceholder {
}
