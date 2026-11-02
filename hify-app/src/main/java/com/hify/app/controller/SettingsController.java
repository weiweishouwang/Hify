package com.hify.app.controller;

import com.hify.app.api.SettingsResp;
import com.hify.app.service.SettingsService;
import com.hify.shared.web.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统设置。
 *
 * <p>只读：版本、依赖状态、关键运行参数。
 *
 * <p>放在 {@code hify-app} 而不是业务模块：它不属于任何业务域，
 * 是应用级别的基础设施接口。业务模块不得依赖本模块（见 CLAUDE.md §3.2）。
 */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    /**
     * 系统设置。
     *
     * @return 版本、依赖状态与关键运行参数
     */
    @GetMapping
    public Result<SettingsResp> settings() {
        return Result.ok(settings.settings());
    }
}
