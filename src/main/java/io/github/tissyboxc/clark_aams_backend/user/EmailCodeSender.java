package io.github.tissyboxc.clark_aams_backend.user;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

@Service
public class EmailCodeSender {
    private static final Logger log = LoggerFactory.getLogger(EmailCodeSender.class);

    private final EmailConfigStore configStore;
    private final RestClient restClient;

    public EmailCodeSender(EmailConfigStore configStore) {
        this.configStore = configStore;
        this.restClient = RestClient.create();
    }

    public void sendVerificationCode(String email, String code) {
        EmailConfigStore.StoredEmailConfig config = configStore.runtimeConfig();
        if (!config.enabled()) {
            log.info("Email sender is not configured; verification code generated for {}", email);
            return;
        }
        if (isBlank(config.endpoint()) || isBlank(config.senderAddress()) || isBlank(config.apiKey())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "邮箱发送配置不完整");
        }

        if (isSmtpProvider(config)) {
            sendBySmtp(config, email, code);
            return;
        }
        sendByHttpProvider(config, email, code);
    }

    private void sendBySmtp(EmailConfigStore.StoredEmailConfig config, String email, String code) {
        SmtpEndpoint endpoint = parseSmtpEndpoint(config.endpoint());
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(endpoint.host());
        sender.setPort(endpoint.port());
        sender.setUsername(config.senderAddress());
        sender.setPassword(config.apiKey());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
        if (endpoint.ssl()) {
            properties.put("mail.smtp.ssl.enable", "true");
        } else {
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.starttls.required", "false");
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(config.senderAddress(), displaySenderName(config));
            helper.setTo(email);
            helper.setSubject(subject(config));
            helper.setText(textBody(code), htmlBody(config, email, code));
            sender.send(message);
            log.info("Verification email sent to {} by SMTP {}", email, endpoint.host());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "邮箱验证码发送失败");
        }
    }

    private void sendByHttpProvider(EmailConfigStore.StoredEmailConfig config, String email, String code) {
        Map<String, Object> payload = Map.of(
                "to", email,
                "code", code,
                "subject", subject(config),
                "senderAddress", config.senderAddress(),
                "senderName", displaySenderName(config),
                "text", textBody(code),
                "html", htmlBody(config, email, code)
        );
        try {
            restClient.post()
                    .uri(config.endpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (!isBlank(config.apiKey())) {
                            headers.setBearerAuth(config.apiKey());
                        }
                    })
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Verification email sent to {} by HTTP provider {}", email, config.provider());
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "邮箱验证码发送失败");
        }
    }

    private boolean isSmtpProvider(EmailConfigStore.StoredEmailConfig config) {
        String provider = normalize(config.provider()).toLowerCase();
        String endpoint = normalize(config.endpoint()).toLowerCase();
        return provider.contains("smtp")
                || provider.contains("qq")
                || endpoint.startsWith("smtp://")
                || endpoint.startsWith("smtps://")
                || endpoint.startsWith("smtp.");
    }

    private SmtpEndpoint parseSmtpEndpoint(String rawEndpoint) {
        String value = normalize(rawEndpoint);
        try {
            URI uri = value.contains("://") ? new URI(value) : new URI("smtps://" + value);
            String host = uri.getHost();
            if (isBlank(host)) {
                host = value.split(":", 2)[0];
            }
            boolean ssl = !"smtp".equalsIgnoreCase(uri.getScheme());
            int port = uri.getPort() > 0 ? uri.getPort() : (ssl ? 465 : 587);
            return new SmtpEndpoint(host, port, ssl);
        } catch (URISyntaxException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "邮箱 SMTP endpoint 配置无效");
        }
    }

    private String subject(EmailConfigStore.StoredEmailConfig config) {
        return isBlank(config.verificationSubject()) ? "Clark AAMS QQ 邮箱验证码" : config.verificationSubject();
    }

    private String displaySenderName(EmailConfigStore.StoredEmailConfig config) {
        return isBlank(config.senderName()) ? "Clark AAMS" : config.senderName();
    }

    private String textBody(String code) {
        return """
                您正在进行 Clark AAMS QQ 邮箱认定。

                验证码：%s

                如果这不是你的操作，请忽略本邮件。
                """.formatted(code);
    }

    private String htmlBody(EmailConfigStore.StoredEmailConfig config, String email, String code) {
        String template = config.htmlTemplate();
        if (isBlank(template)) {
            template = """
                    <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;line-height:1.7;color:#3f2431">
                      <h2 style="margin:0 0 12px">Clark AAMS QQ 邮箱认定</h2>
                      <p>您正在进行 QQ 邮箱认定，请在页面中输入以下验证码：</p>
                      <div style="display:inline-block;padding:12px 18px;border-radius:8px;background:#fff0f7;color:#c93b78;font-size:28px;font-weight:800;letter-spacing:4px">{{code}}</div>
                      <p style="color:#8d6274">如果这不是你的操作，请忽略本邮件。</p>
                    </div>
                    """;
        }
        return template
                .replace("{{code}}", code)
                .replace("${code}", code)
                .replace("%CODE%", code)
                .replace("{{email}}", email)
                .replace("${email}", email)
                .replace("{{subject}}", subject(config))
                .replace("${subject}", subject(config))
                .replace("{{senderName}}", displaySenderName(config))
                .replace("${senderName}", displaySenderName(config));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return normalize(value).isBlank();
    }

    private record SmtpEndpoint(String host, int port, boolean ssl) {
    }
}
