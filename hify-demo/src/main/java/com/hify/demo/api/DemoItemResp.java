package com.hify.demo.api;

import java.time.LocalDateTime;

/**
 * 演示条目的响应对象。
 *
 * <p>时间字段由 shared 的 Jackson 配置统一成 ISO 8601
 * （{@code 2026-10-30T15:52:42}），前端不用再转。
 */
public class DemoItemResp {

    private Long id;

    private String name;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
