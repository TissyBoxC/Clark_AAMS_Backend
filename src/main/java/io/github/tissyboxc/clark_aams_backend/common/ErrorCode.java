package io.github.tissyboxc.clark_aams_backend.common;

public enum ErrorCode {
    OK(0, "ok"),
    BAD_REQUEST(40001, "请求参数错误"),
    LOGIN_INVALID(40101, "登录状态无效或已过期，请重新登录"),
    /**
     * 账号或密码不正确。
     *
     * <p>和 {@link #LOGIN_INVALID} 分开，因为两者虽然都是 401，
     * 但对用户的意义完全不同：40101 是「你登录过，但会话没了」，
     * 用户要重新登录；40103 是「你正在登录，但填的账号密码不对」，
     * 用户要改输入。混用会让刚输错密码的人在登录页看到
     * 「登录状态已失效」——完全对不上他在做的事。
     */
    BAD_CREDENTIALS(40103, "账号或密码不正确"),
    FORBIDDEN(40301, "没有权限执行该操作"),
    SCHOOL_NOT_FOUND(40401, "学校不存在"),
    IMPORTER_NOT_FOUND(40402, "当前学校暂未配置教务系统导入"),
    USER_NOT_FOUND(40403, "用户不存在"),
    COURSE_NOT_FOUND(40404, "课程不存在"),
    USER_EMAIL_CONFLICT(40901, "QQ 邮箱已被绑定"),
    /** 单用户配额用尽（如识别次数），需等待重置或联系管理员。 */
    QUOTA_EXCEEDED(42901, "今日额度已用完，请明天再试"),
    /** 请求过于频繁（短时限流），稍后可重试。 */
    RATE_LIMITED(42902, "操作过于频繁，请稍后再试"),
    ACADEMIC_REQUEST_FAILED(50001, "教务系统请求失败"),
    COURSE_PARSE_FAILED(50002, "课表解析失败"),
    INTERNAL_ERROR(50003, "后端内部错误"),
    AI_REQUEST_FAILED(50004, "AI 请求失败");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
