package com.hify.app.controller;

import com.hify.shared.web.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口。
 *
 * <p>应用自身的存活探针，供前端与外部系统确认服务可用。**与 Actuator 的
 * {@code /actuator/health} 不同**——那个是给 K8s 探针用的，这个是给调用方用的，
 * 返回统一的 {@link Result} 结构。
 *
 * <p>本接口放在 {@code hify-app} 而不是业务模块：它不属于任何业务域，
 * 是应用级别的基础设施接口。业务模块不得依赖本模块（见 CLAUDE.md §3.2）。
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    /**
     * 健康检查。
     *
     * @return 固定文案，说明应用已正常接收请求
     */
    @GetMapping
    public Result<String> health() {
        return Result.ok("Hify is running");
    }
}
