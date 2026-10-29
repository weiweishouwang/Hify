package com.hify.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hify 启动类。
 *
 * <p>{@code scanBasePackages = "com.hify"} 是必须的：启动类位于 {@code com.hify.app}，
 * 而 Spring Boot 默认只扫描启动类所在包及其子包——不显式指定就扫不到其他 8 个业务模块的组件。
 *
 * <p>{@code @MapperScan("com.hify.**.mapper")} 让 MyBatis-Plus 一次性扫描所有模块
 * 的 Mapper 包。各模块的 Mapper 基类统一继承 shared 的 BaseDO，事件流表因是分区表
 * 不继承（见 CLAUDE.md §3.3）。
 *
 * <p>本模块是唯一可执行模块（唯一带 {@code spring-boot-maven-plugin:repackage}），
 * 业务模块不得反向依赖它（见 CLAUDE.md §3.2，由 ArchUnit 检查）。
 */
@SpringBootApplication(scanBasePackages = "com.hify")
@MapperScan("com.hify.**.mapper")
public class HifyApplication {

    public static void main(String[] args) {
        SpringApplication.run(HifyApplication.class, args);
    }
}
