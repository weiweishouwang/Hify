/**
 * tool 模块 · 工具 —— Tool 抽象 + ToolProvider SPI + 内置与 HTTP 两种来源。
 *
 * <p>本模块对其他模块的唯一可见面：公开契约。
 *
 * <p>硬约束（CLAUDE.md §3.2）：
 * <ul>
 *   <li>只放 interface、纯 DTO 与 event</li>
 *   <li>禁止 Spring 注解与 ORM 注解——契约必须能脱离框架独立序列化</li>
 *   <li>禁止引用本模块 internal 下的任何类</li>
 *   <li>禁止出现数据对象（DO）</li>
 *   <li>方法签名必须「远程调用友好」：参数与返回值全是 DTO、无重载、参数不超过 3 个、无 checked exception</li>
 * </ul>
 *
 * <p>模块拆成独立服务时，本包会原样复制过去成为 RPC 契约的来源——改接口签名前先想清楚这一点。</p>
 *
 * <p>本文件是占位类，说明本包的职责与约束；写实际代码时替换掉它。</p>
 */
public class ApiPlaceholder {
}
