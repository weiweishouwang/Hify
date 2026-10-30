package com.hify.demo.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 更新演示条目的请求。
 *
 * <p>更新是全量更新（PUT 语义）：两个字段都要给，传 {@code null} 会被
 * {@code @Valid} 挡下来，而不是悄悄当成「不修改」。
 */
public class DemoItemUpdateReq {

    @NotBlank(message = "名称不能为空")
    @Size(max = 128, message = "名称最长 128 个字符")
    private String name;

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态最小为 0")
    @Max(value = 2, message = "状态最大为 2")
    private Integer status;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
