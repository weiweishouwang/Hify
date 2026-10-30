package com.hify.demo.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建演示条目的请求。
 *
 * <p>放在 {@code api} 包是因为它是对外契约的一部分——别的模块要调这个接口，
 * 就得能构造这个请求对象。它不带任何 ORM 注解，只有校验约束，因此可以脱离
 * Spring 与 MyBatis 独立使用。
 */
public class DemoItemCreateReq {

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
