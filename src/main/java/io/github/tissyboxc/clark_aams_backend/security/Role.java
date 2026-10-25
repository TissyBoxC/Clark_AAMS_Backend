package io.github.tissyboxc.clark_aams_backend.security;

/**
 * 身份角色。
 *
 * <p>背景（审查文档 P1）：系统里此前有两套互不相干的凭据——
 * 用户侧（`users` 表 + 登录码）和管理员侧（`admin-config.json`），
 * 前端也对应两个独立入口 `/auth/login` 与 `/admin/login`，
 * 于是「登录后按身份进入不同工作台」这件事从来没有真正实现过。
 *
 * <p>本枚举把身份收敛成显式角色，供过滤器与前端统一判定。
 * 注意：目前<b>两套凭据仍分开存储</b>（合并账号表属于 P1 的后续步骤），
 * 但「谁能访问什么」从此只有一个判据。
 */
public enum Role {
    /** 未登录。 */
    GUEST("guest"),
    /** 普通用户：可用课表、导入、识别、取件码、做题辅助。 */
    USER("user"),
    /** 管理员：额外可访问 `/api/v1/admin/**`。 */
    ADMIN("admin");

    private final String wire;

    Role(String wire) {
        this.wire = wire;
    }

    /** 对外传输值，前端据此路由。 */
    public String wire() {
        return wire;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }

    public boolean isAuthenticated() {
        return this != GUEST;
    }

    /** 解析前端/配置传入的角色名，未知值返回 GUEST（失败即最小权限）。 */
    public static Role fromWire(String value) {
        if (value == null) {
            return GUEST;
        }
        String normalized = value.trim().toLowerCase();
        for (Role role : values()) {
            if (role.wire.equals(normalized)) {
                return role;
            }
        }
        return GUEST;
    }
}
