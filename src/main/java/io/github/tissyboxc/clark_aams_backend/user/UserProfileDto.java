package io.github.tissyboxc.clark_aams_backend.user;

/**
 * 用户资料。
 *
 * <p>安全约定（审查条目 F4）：**默认不回传登录码**。
 * 登录码是等价于密码的长期凭据，一旦出现在 `/user/me` 这类
 * 高频读取接口的响应里，就会进入日志、代理缓存、浏览器历史与
 * 崩溃上报。需要取回登录码时，只允许在「刚刚完成登录」的响应
 * （{@link UserLoginResponse}）或显式轮换接口中返回一次。
 *
 * @param loginCode 为空串表示「本次不返回登录码」；仅登录/轮换场景填充
 */
public record UserProfileDto(
        long id,
        String username,
        String email,
        String loginCode,
        boolean emailBound,
        boolean emailVerified,
        long questionAnalysisCount
) {

    /** 生成不含登录码的对外资料，供 `/user/me` 等读取接口使用。 */
    public UserProfileDto withoutLoginCode() {
        return new UserProfileDto(
                id,
                username,
                email,
                "",
                emailBound,
                emailVerified,
                questionAnalysisCount
        );
    }
}
