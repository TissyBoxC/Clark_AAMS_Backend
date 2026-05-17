package io.github.tissyboxc.clark_aams_backend.user;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

@Service
public class UserAccountService {
    private static final String LOGIN_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int LOGIN_CODE_LENGTH = 12;
    private static final int EMAIL_CODE_LENGTH = 6;

    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserAccountService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public synchronized UserProfileDto register(UserRegisterRequest request) {
        String username = normalize(request == null ? null : request.username());
        String password = normalize(request == null ? null : request.password());
        String email = normalizeEmail(request == null ? null : request.qqEmail());
        if (username.isBlank() || password.isBlank() || email.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名、密码和 QQ 邮箱不能为空");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(ErrorCode.USER_EMAIL_CONFLICT);
        }

        UserRepository.UserRecord record = new UserRepository.UserRecord(
                userRepository.nextId(),
                username,
                hashPassword(password),
                email,
                generateUniqueLoginCode(),
                false,
                false,
                "",
                0
        );
        return toProfile(userRepository.save(record));
    }

    public UserProfileDto login(UserLoginRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.LOGIN_INVALID);
        }
        String loginCode = normalize(request.loginCode());
        if (!loginCode.isBlank()) {
            return userRepository.findByLoginCode(loginCode)
                    .map(this::toProfile)
                    .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_INVALID, "登录码无效"));
        }

        String email = normalizeEmail(request.qqEmail());
        String password = normalize(request.password());
        if (!email.isBlank() || !password.isBlank()) {
            if (email.isBlank() || password.isBlank()) {
                throw new BusinessException(ErrorCode.LOGIN_INVALID, "请输入 QQ 邮箱和密码");
            }
            return userRepository.findByEmail(email)
                    .filter(record -> constantTimeEquals(record.passwordHash(), hashPassword(password)))
                    .map(this::toProfile)
                    .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_INVALID, "QQ 邮箱或密码错误"));
        }

        String username = normalize(request.username());
        if (username.isBlank() || password.isBlank()) {
            throw new BusinessException(ErrorCode.LOGIN_INVALID, "请输入登录码，或输入 QQ 邮箱和密码");
        }
        return userRepository.findByUsernameAndPassword(username, hashPassword(password))
                .map(this::toProfile)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_INVALID, "用户名或密码错误"));
    }

    public UserProfileDto getById(long id) {
        return userRepository.findById(id)
                .map(this::toProfile)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    public UserProfileDto getByLoginCode(String loginCode) {
        String normalized = normalize(loginCode);
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.LOGIN_INVALID);
        }
        return userRepository.findByLoginCode(normalized)
                .map(this::toProfile)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_INVALID));
    }

    public UserProfileDto requestEmailCode(long id, String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "QQ 邮箱不能为空");
        }
        userRepository.findByEmail(normalizedEmail)
                .filter(record -> record.id() != id)
                .ifPresent(record -> {
                    throw new BusinessException(ErrorCode.USER_EMAIL_CONFLICT);
                });

        UserRepository.UserRecord existing = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String code = generateEmailCode();
        UserRepository.UserRecord updated = new UserRepository.UserRecord(
                existing.id(),
                existing.username(),
                existing.passwordHash(),
                normalizedEmail,
                existing.loginCode(),
                false,
                false,
                code,
                existing.questionAnalysisCount()
        );
        userRepository.save(updated);
        return toProfile(updated);
    }

    public EmailChallenge requestEmailChallenge(long id, String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "QQ 邮箱不能为空");
        }
        userRepository.findByEmail(normalizedEmail)
                .filter(record -> record.id() != id)
                .ifPresent(record -> {
                    throw new BusinessException(ErrorCode.USER_EMAIL_CONFLICT);
                });

        UserRepository.UserRecord existing = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String code = generateEmailCode();
        UserRepository.UserRecord updated = new UserRepository.UserRecord(
                existing.id(),
                existing.username(),
                existing.passwordHash(),
                normalizedEmail,
                existing.loginCode(),
                false,
                false,
                code,
                existing.questionAnalysisCount()
        );
        userRepository.save(updated);
        return new EmailChallenge(toProfile(updated), code);
    }

    public UserProfileDto confirmEmail(long id, String code) {
        String normalizedCode = normalize(code);
        if (normalizedCode.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码不能为空");
        }
        UserRepository.UserRecord existing = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!normalizedCode.equals(normalize(existing.emailVerifyCode()))) {
            throw new BusinessException(ErrorCode.LOGIN_INVALID, "验证码不正确");
        }
        UserRepository.UserRecord updated = new UserRepository.UserRecord(
                existing.id(),
                existing.username(),
                existing.passwordHash(),
                existing.email(),
                existing.loginCode(),
                true,
                true,
                "",
                existing.questionAnalysisCount()
        );
        userRepository.save(updated);
        return toProfile(updated);
    }

    public UserProfileDto incrementQuestionAnalysisCount(long id) {
        return toProfile(userRepository.incrementQuestionAnalysisCount(id));
    }

    private UserProfileDto toProfile(UserRepository.UserRecord record) {
        return new UserProfileDto(
                record.id(),
                record.username(),
                record.email(),
                record.loginCode(),
                record.emailBound(),
                record.emailVerified(),
                record.questionAnalysisCount()
        );
    }

    private String generateUniqueLoginCode() {
        for (int attempt = 0; attempt < 24; attempt++) {
            String candidate = randomCode(LOGIN_CODE_LENGTH, LOGIN_CODE_ALPHABET);
            if (userRepository.findByLoginCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "登录码生成失败");
    }

    private String generateEmailCode() {
        return randomCode(EMAIL_CODE_LENGTH, "0123456789");
    }

    private String randomCode(int length, String alphabet) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet.charAt(secureRandom.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(normalize(password).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to hash password", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                normalize(left).getBytes(StandardCharsets.UTF_8),
                normalize(right).getBytes(StandardCharsets.UTF_8)
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeEmail(String value) {
        return normalize(value).toLowerCase();
    }

    public record EmailChallenge(UserProfileDto user, String code) {
    }
}
