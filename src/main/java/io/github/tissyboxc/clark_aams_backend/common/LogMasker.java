package io.github.tissyboxc.clark_aams_backend.common;

/**
 * 日志脱敏工具（P5-4）。
 *
 * <p>后端日志目前会把**完整邮箱**写进日志，例如：
 * <pre>
 * log.info("Verification email sent to {} by SMTP {}", email, endpoint.host());
 * </pre>
 * 日志是相对开放的信息：会进日志聚合平台、被运维/开发查看、
 * 有时被打包进 issue 附件。QQ 邮箱本身就是实名身份，完整地址属于个人信息，
 * 不应该在「发送邮件成功」这种运维日志里全量出现。
 *
 * <p>这里做最小化脱敏：保留首字符和前 2 位域名，剩下的打码，
 * 既能对上一个具体用户（排障时需要），又不暴露完整地址。
 */
public final class LogMasker {

    private LogMasker() {
    }

    /**
     * 邮箱脱敏：{@code 123456789@qq.com} → {@code 1***9@qq.com}。
     *
     * <p>输入为空或格式异常时返回 {@code ***}，绝不原样返回——
     * 宁可丢掉排障信息，也不要把可能的敏感值直接写进日志。
     */
    public static String email(String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }
        String trimmed = value.trim();
        int at = trimmed.lastIndexOf('@');
        if (at <= 0 || at == trimmed.length() - 1) {
            return "***";
        }
        String local = trimmed.substring(0, at);
        String domain = trimmed.substring(at + 1);

        String maskedLocal;
        if (local.length() <= 2) {
            maskedLocal = local.charAt(0) + "***";
        } else {
            maskedLocal = local.charAt(0) + "***" + local.charAt(local.length() - 1);
        }
        return maskedLocal + "@" + domain;
    }

    /**
     * 登录码/验证码脱敏：只保留前 2 位。
     *
     * <p>登录码等同于凭据，理论上不该出现在日志里；
     * 若确实需要记录（例如排障「换发成功」），也只保留可识别的前缀。
     */
    public static String secret(String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 2) {
            return "**";
        }
        return trimmed.substring(0, 2) + "***";
    }
}
