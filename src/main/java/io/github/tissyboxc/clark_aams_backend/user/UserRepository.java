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

    record UserRecord(
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
    }
}
