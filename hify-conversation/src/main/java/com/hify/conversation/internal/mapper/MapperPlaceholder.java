/**
 * conversation 模块 · 对话引擎 —— 会话、消息、流式对话编排。
 *
 * <p>数据访问层。
 *
 * <p>硬约束（CLAUDE.md §3.2）：
 * <ul>
 *   <li>只放 Mapper 与自定义 SQL</li>
 *   <li>禁止业务逻辑</li>
 *   <li>SQL 里的表名必须以本模块的表前缀开头（conv_）</li>
 *   <li>禁止查询其他模块的表</li>
 * </ul>
 *
 * <p>数据访问不跨模块——需要别的模块的数据时走对方的 api 接口。</p>
 *
 * <p>本文件是占位类，说明本包的职责与约束；写实际代码时替换掉它。</p>
 */
public class MapperPlaceholder {
}
