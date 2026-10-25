package io.github.tissyboxc.clark_aams_backend.user;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    /**
     * 只读地预览「下一个 id 大概是多少」。
     *
     * <p><b>不要用它来分配 id 再 save</b>：这两步之间没有原子性，
     * 并发时会拿到重复 id。正确做法是 {@link #insert(UserRecord)}。
     * 保留此方法仅供测试与诊断。
     */
    long nextId();

    /**
     * 原子地分配 id 并插入用户，返回带真实 id 的记录。
     *
     * <p>这是注册流程唯一应该使用的写入入口：id 由数据库自增分配，
     * 「取号」与「插入」在同一条 SQL 内完成，不存在并发窗口。
     *
     * <p>传入记录的 {@code id} 字段会被忽略。
     */
    UserRecord insert(UserRecord user);

    UserRecord save(UserRecord user);

    Optional<UserRecord> findById(long id);

    Optional<UserRecord> findByUsernameAndPassword(String username, String passwordHash);

    /**
     * 按用户名查记录（不校验密码）。
     *
     * <p>加盐散列之后，密码无法在 SQL 层比较——盐是每条记录各自的，
     * 必须先取出记录、用它的盐重算散列再比对。
     */
    Optional<UserRecord> findByUsername(String username);

    /** 更新指定用户的密码散列（用于历史散列的机会式升级）。 */
    void updatePasswordHash(long id, String passwordHash);

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
