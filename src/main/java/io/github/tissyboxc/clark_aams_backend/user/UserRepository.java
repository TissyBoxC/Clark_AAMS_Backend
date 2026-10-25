package io.github.tissyboxc.clark_aams_backend.user;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    long nextId();

    UserRecord save(UserRecord user);

    Optional<UserRecord> findById(long id);

    Optional<UserRecord> findByUsernameAndPassword(String username, String passwordHash);

    Optional<UserRecord> findByLoginCode(String loginCode);

    Optional<UserRecord> findByEmail(String email);

    List<UserRecord> findAll();

    UserRecord incrementQuestionAnalysisCount(long id);

    /**
     * 吊销并重新签发登录码（「退出所有设备」）。
     *
     * <p>旧登录码立即失效；已建立的 session 不受影响（那是另一套凭据），
     * 但会话清理由 P1 的 token 体系解决，此处只保证「码」失效。
     */
    UserRecord rotateLoginCode(long id, String newLoginCode);

    record UserRecord(
            long id,
            String username,
            String passwordHash,
            String email,
            String loginCode,
            boolean emailBound,
            boolean emailVerified,
            String emailVerifyCode,
            long questionAnalysisCount,
            /** 登录码签发时间戳（毫秒）。0 表示历史数据，视为永不过期。 */
            long loginCodeIssuedAt
    ) {
        /** 兼容旧构造：默认签发时间为 0（不过期）。 */
        public UserRecord(
                long id,
                String username,
                String passwordHash,
                String email,
                String loginCode,
                boolean emailBound,
                boolean emailVerified,
                String emailVerifyCode,
                long questionAnalysisCount
        ) {
            this(id, username, passwordHash, email, loginCode,
                    emailBound, emailVerified, emailVerifyCode,
                    questionAnalysisCount, 0L);
        }
    }
}
