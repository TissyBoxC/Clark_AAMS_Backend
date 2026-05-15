package io.github.tissyboxc.clark_aams_backend.common;

public enum ErrorCode {
    OK(0, "ok"),
    BAD_REQUEST(40001, "请求参数错误"),
    LOGIN_INVALID(40101, "登录状态无效或已过期，请重新登录"),
    SCHOOL_NOT_FOUND(40401, "学校不存在"),
    IMPORTER_NOT_FOUND(40402, "当前学校暂未配置教务系统导入"),
    ACADEMIC_REQUEST_FAILED(50001, "教务系统请求失败"),
    COURSE_PARSE_FAILED(50002, "课表解析失败"),
    INTERNAL_ERROR(50003, "后端内部错误");

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
