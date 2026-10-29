package com.hify.arch;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块边界检查。
 *
 * <p>这些规则把 CLAUDE.md §3.2「分层与跨模块」的约定变成 CI 里会失败的断言。
 * 放在 {@code hify-app} 的测试目录——它是唯一依赖全部业务模块的模块，
 * 因此能扫到所有类的字节码。
 *
 * <p>规则与文档的对应：
 * <ul>
 *   <li>M1 / M2 —— 跨模块只走对方 {@code api} 包，不得碰 {@code internal}</li>
 *   <li>M2 —— 不得访问其他模块的 Mapper</li>
 *   <li>M4 —— {@code api} 层不得暴露 DO</li>
 *   <li>M5 —— 依赖方向单向：能力层不得反向依赖编排层</li>
 *   <li>M6 —— {@code tool} 不得依赖编排层</li>
 *   <li>M9 / M10 —— 认证约束与 {@code api} 包的远程调用友好性</li>
 * </ul>
 */
class ModuleBoundaryTest {

    /** 能力层模块（被编排层调用的那些）。 */
    private static final List<String> CAPABILITY = List.of("ai", "tool", "knowledge", "agent", "mcp");

    /** 编排层模块。 */
    private static final List<String> ORCHESTRATION = List.of("conversation", "workflow");

    /** 全部业务模块（不含 shared 与 app）。 */
    private static final List<String> BUSINESS = List.of(
            "ai", "tool", "knowledge", "mcp", "agent", "conversation", "workflow");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.hify");
    }

    /** {@code com.hify.<module>[.<sub>]} → {@code <module>}。 */
    private static String moduleOf(String packageName) {
        Matcher m = Pattern.compile("^com\\.hify\\.([a-z][a-z0-9]*)(?:\\..*)?$").matcher(packageName);
        return m.matches() ? m.group(1) : packageName;
    }

    /** 匹配 {@code com.hify.x.api} 与 {@code com.hify.x.api.dto}，但不匹配 {@code x.apimpl}。 */
    private static boolean isApiPackage(String pkg) {
        return pkg.matches(".*\\.api(\\..*)?");
    }

    private static boolean isInternalPackage(String pkg) {
        return pkg.matches(".*\\.internal(\\..*)?");
    }

    private static boolean isMapperPackage(String pkg) {
        return pkg.matches(".*\\.mapper(\\..*)?");
    }

    /** 收集「源类 → 目标类」的跨模块依赖描述，便于断言时给出可读的失败信息。 */
    private static List<String> describeViolations(java.util.function.Predicate<Violation> filter) {
        List<String> violations = new ArrayList<>();
        for (JavaClass source : classes) {
            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                Violation violation = new Violation(source, target);
                if (filter.test(violation)) {
                    violations.add(source.getFullName() + " -> " + target.getFullName());
                }
            }
        }
        return violations;
    }

    private record Violation(JavaClass source, JavaClass target) {
        String sourceModule() {
            return moduleOf(source.getPackageName());
        }

        String targetModule() {
            return moduleOf(target.getPackageName());
        }
    }

    // ── M1 / M2：跨模块不得依赖对方的 internal ─────────────────────────

    @Test
    @DisplayName("跨模块不得依赖对方的 internal 包")
    void 跨模块不得依赖对方的internal包() {
        List<String> violations = describeViolations(violation ->
                !violation.sourceModule().equals(violation.targetModule())
                        && isInternalPackage(violation.target().getPackageName()));

        assertThat(violations).as("跨模块只能通过对方的 api 包调用").isEmpty();
    }

    // ── api 包不得依赖 internal ───────────────────────────────────────

    @Test
    @DisplayName("api 包不得依赖任何 internal 包")
    void api包不得依赖internal包() {
        List<String> violations = new ArrayList<>();
        for (JavaClass source : classes) {
            if (!isApiPackage(source.getPackageName())) {
                continue;
            }
            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (isInternalPackage(target.getPackageName())) {
                    violations.add(source.getFullName() + " -> " + target.getFullName());
                }
            }
        }

        assertThat(violations).as("api 包是模块的公开契约，不得引用内部实现").isEmpty();
    }

    // ── api 包不得出现 Spring / ORM 注解 ──────────────────────────────

    @Test
    @DisplayName("api 包不得依赖 Spring 注解或 ORM 注解")
    void api包不得依赖Spring或ORM注解() {
        List<String> violations = new ArrayList<>();
        for (JavaClass source : classes) {
            if (!isApiPackage(source.getPackageName())) {
                continue;
            }
            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String pkg = target.getPackageName();
                if (pkg.startsWith("org.springframework.")
                        || pkg.startsWith("com.baomidou.mybatisplus.annotation")
                        || pkg.startsWith("jakarta.persistence")) {
                    violations.add(source.getFullName() + " -> " + target.getFullName());
                }
            }
        }

        assertThat(violations).as("api 包必须能脱离 Spring 与 ORM 独立序列化").isEmpty();
    }

    // ── M4：api 层不得暴露 DO ─────────────────────────────────────────

    @Test
    @DisplayName("api 层不得暴露数据对象")
    void api层不得暴露DO() {
        List<String> violations = new ArrayList<>();
        for (JavaClass source : classes) {
            if (!isApiPackage(source.getPackageName())) {
                continue;
            }
            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (target.getPackageName().matches(".*\\.internal\\.entity(\\..*)?")) {
                    violations.add(source.getFullName() + " -> " + target.getFullName());
                }
            }
        }

        assertThat(violations).as("跨模块只传 DTO，不传 ORM 实体").isEmpty();
    }

    // ── M2：不得访问其他模块的 Mapper ─────────────────────────────────

    @Test
    @DisplayName("不得访问其他模块的 Mapper")
    void 不得访问其他模块的Mapper() {
        List<String> violations = describeViolations(violation ->
                !violation.sourceModule().equals(violation.targetModule())
                        && isMapperPackage(violation.target().getPackageName()));

        assertThat(violations).as("跨模块取数必须走对方的 api 接口").isEmpty();
    }

    // ── M5：能力层不得反向依赖编排层 ─────────────────────────────────

    @Test
    @DisplayName("能力层不得依赖编排层")
    void 能力层不得依赖编排层() {
        List<String> violations = describeViolations(violation ->
                CAPABILITY.contains(violation.sourceModule())
                        && ORCHESTRATION.contains(violation.targetModule()));

        assertThat(violations).as("依赖方向只能从编排层指向能力层").isEmpty();
    }

    // ── M5：ai 不得依赖其它能力模块 ──────────────────────────────────

    @Test
    @DisplayName("ai 模块不得依赖 knowledge / tool / agent / mcp")
    void ai模块不得依赖组合能力模块() {
        List<String> forbidden = List.of("knowledge", "tool", "agent", "mcp");
        List<String> violations = describeViolations(violation ->
                violation.sourceModule().equals("ai") && forbidden.contains(violation.targetModule()));

        assertThat(violations)
                .as("ai 必须是纯模型调用能力，组合逻辑只能在编排层，否则 ai ↔ knowledge 成环")
                .isEmpty();
    }

    // ── M6：tool 不得依赖编排层 ──────────────────────────────────────

    @Test
    @DisplayName("tool 模块不得依赖编排层")
    void tool模块不得依赖编排层() {
        List<String> violations = describeViolations(violation ->
                violation.sourceModule().equals("tool") && ORCHESTRATION.contains(violation.targetModule()));

        assertThat(violations).as("tool 定义 SPI 由来源方实现，不得反向依赖编排层").isEmpty();
    }

    // ── 业务模块不得依赖 hify-app ────────────────────────────────────

    @Test
    @DisplayName("业务模块不得依赖装配模块 hify-app")
    void 业务模块不得依赖装配模块() {
        List<String> violations = describeViolations(violation ->
                BUSINESS.contains(violation.sourceModule()) && violation.targetModule().equals("app"));

        assertThat(violations).as("hify-app 是顶层装配，业务模块不得反向依赖它").isEmpty();
    }

    // ── shared 不得依赖业务模块 ──────────────────────────────────────

    @Test
    @DisplayName("shared 不得依赖任何业务模块")
    void shared不得依赖业务模块() {
        List<String> violations = describeViolations(violation ->
                violation.sourceModule().equals("shared") && BUSINESS.contains(violation.targetModule()));

        assertThat(violations).as("shared 是叶子模块，依赖业务模块会成环").isEmpty();
    }
}
