package com.hify.shared.internal.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.shared.mybatis.BaseEntity;

/**
 * 用户的数据对象。
 *
 * <p>表名是 {@code user} —— MySQL 里 {@code USER} 是非保留字，可以直接用作表名，
 * 但自定义 SQL 里还是加上反引号更保险。
 */
@TableName("user")
public class UserDO extends BaseEntity {

    /** 对外 ID，ULID。 */
    @TableField("public_id")
    private String publicId;

    /** 登录名。 */
    @TableField("username")
    private String username;

    /** 显示名。 */
    @TableField("nickname")
    private String nickname;

    /** 邮箱。 */
    @TableField("email")
    private String email;

    /** 是否管理员。 */
    @TableField("is_admin")
    private Integer isAdmin;

    /** 状态：active / disabled。 */
    @TableField("status")
    private String status;

    /** 最近登录时间(UTC)。 */
    @TableField("last_login_at")
    private java.time.LocalDateTime lastLoginAt;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getIsAdmin() {
        return isAdmin;
    }

    public void setIsAdmin(Integer isAdmin) {
        this.isAdmin = isAdmin;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public java.time.LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(java.time.LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
