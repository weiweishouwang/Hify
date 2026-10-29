/**
 * hify-app 模块 · 装配 —— 启动类、Spring 装配、静态资源与反代配置。
 *
 * <p>本模块是唯一可执行模块（唯一带 spring-boot-maven-plugin:repackage 的模块），
 * 依赖全部业务模块，把它们装配进同一个 Spring 上下文。
 *
 * <p>硬约束（CLAUDE.md §3.2）：
 * <ul>
 *   <li>业务模块不得反向依赖本模块——依赖方向只能由 app 指向业务模块</li>
 *   <li>本包只做装配，不放业务逻辑</li>
 *   <li>启动类使用 @SpringBootApplication(scanBasePackages = "com.hify")，扫描全部模块的组件</li>
 * </ul>
 *
 * <p>本文件是占位类，说明本包的职责与约束；写实际代码时替换掉它。
 */
public class ConfigPlaceholder {
}
