package com.hify.app.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统设置页的数据。
 *
 * <p>「设置」页是只读的：它回答三个问题 —— 我跑的是哪个版本、依赖通不通、
 * 关键的运行参数是多少。一期没有需要用户在页面上改的配置（认证方案还没定，
 * 也没有多租户），所以没有写入接口。
 */
public class SettingsResp {

    /** 应用信息。 */
    private AppInfo app;

    /** 依赖状态。 */
    private List<DependencyStatus> dependencies;

    /** 关键运行参数。 */
    private Map<String, String> parameters;

    public AppInfo getApp() {
        return app;
    }

    public void setApp(AppInfo app) {
        this.app = app;
    }

    public List<DependencyStatus> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<DependencyStatus> dependencies) {
        this.dependencies = dependencies;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    /**
     * 应用自身的信息。
     */
    public static class AppInfo {

        /** 版本号。 */
        private String version;

        /** 构建时间(UTC)。 */
        private String buildTime;

        /** 启动时间(UTC)。 */
        private LocalDateTime startedAt;

        /** 已运行秒数。 */
        private Long uptimeSeconds;

        /** Java 版本。 */
        private String javaVersion;

        /** 生效的 profile：prod / dev / default。 */
        private String profile;

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getBuildTime() {
            return buildTime;
        }

        public void setBuildTime(String buildTime) {
            this.buildTime = buildTime;
        }

        public LocalDateTime getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(LocalDateTime startedAt) {
            this.startedAt = startedAt;
        }

        public Long getUptimeSeconds() {
            return uptimeSeconds;
        }

        public void setUptimeSeconds(Long uptimeSeconds) {
            this.uptimeSeconds = uptimeSeconds;
        }

        public String getJavaVersion() {
            return javaVersion;
        }

        public void setJavaVersion(String javaVersion) {
            this.javaVersion = javaVersion;
        }

        public String getProfile() {
            return profile;
        }

        public void setProfile(String profile) {
            this.profile = profile;
        }
    }

    /**
     * 一个依赖的状态。
     */
    public static class DependencyStatus {

        /** 依赖名：MySQL / PostgreSQL / Redis。 */
        private String name;

        /** 是否可用。 */
        private Boolean up;

        /** 说明：可用时是延迟，不可用时是原因。 */
        private String detail;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Boolean getUp() {
            return up;
        }

        public void setUp(Boolean up) {
            this.up = up;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }
    }
}
