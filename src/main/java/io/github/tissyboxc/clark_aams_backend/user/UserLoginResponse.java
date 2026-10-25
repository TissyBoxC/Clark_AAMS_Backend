package io.github.tissyboxc.clark_aams_backend.user;

/**
 * 登录成功的响应。
 *
 * <p>{@code loginCode} 只在这里返回一次——它是等价于密码的长期凭据，
 * 不应出现在 `/user/me` 这类高频读取接口中。
 *
 * <p>{@code role}（P1-1）：账号表合并后，管理员也通过这个接口登录，
 * 前端必须能从登录响应直接拿到角色，才能「登录后按身份进入不同工作台」。
 * 没有这个字段时，前端只能再调一次 `/auth/me` 才知道该往哪跳，
 * 多一次往返，且中间会出现一帧「已登录但不知去哪」的空状态。
 */
public record UserLoginResponse(
        long id,
        String username,
        String email,
        String loginCode,
        boolean emailBound,
        boolean emailVerified,
        long questionAnalysisCount,
        String role
) {
    /** 兼容旧构造：不带 role 时视为普通用户。 */
    public UserLoginResponse(
            long id,
            String username,
            String email,
            String loginCode,
            boolean emailBound,
            boolean emailVerified,
            long questionAnalysisCount
    ) {
        this(id, username, email, loginCode, emailBound, emailVerified, questionAnalysisCount, "user");
    }
}
