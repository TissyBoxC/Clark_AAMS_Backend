package io.github.tissyboxc.clark_aams_backend.user;

import io.github.tissyboxc.clark_aams_backend.ClarkAamsBackendApplication;
import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class SQLiteUserRepository implements UserRepository {
    private static final String DEFAULT_FILE_NAME = "users.db";

    private final String configuredPath;
    private Path dbPath;
    private String jdbcUrl;

    public SQLiteUserRepository(@Value("${clark-aams.user.db-path:}") String configuredPath) {
        this.configuredPath = configuredPath;
    }

    @PostConstruct
    public void initialize() {
        dbPath = resolveDbPath();
        try {
            Path parent = dbPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to create user db directory", exception);
        }
        jdbcUrl = "jdbc:sqlite:" + dbPath;
        initSchema();
    }

    @Override
    public synchronized long nextId() {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select coalesce(max(id), 0) + 1 as next_id from users")) {
            return resultSet.next() ? resultSet.getLong("next_id") : 1L;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to allocate next user id", exception);
        }
    }

    @Override
    public synchronized UserRecord save(UserRecord user) {
        String sql = """
                insert into users(id, username, password_hash, email, login_code, email_bound, email_verified, email_verify_code, question_analysis_count)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                  username = excluded.username,
                  password_hash = excluded.password_hash,
                  email = excluded.email,
                  login_code = excluded.login_code,
                  email_bound = excluded.email_bound,
                  email_verified = excluded.email_verified,
                  email_verify_code = excluded.email_verify_code,
                  question_analysis_count = excluded.question_analysis_count
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, user);
            statement.executeUpdate();
            return user;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save user", exception);
        }
    }

    @Override
    public synchronized Optional<UserRecord> findById(long id) {
        return queryOne("select * from users where id = ?", id);
    }

    @Override
    public synchronized Optional<UserRecord> findByUsernameAndPassword(String username, String passwordHash) {
        String sql = "select * from users where username = ? and password_hash = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            try (ResultSet resultSet = statement.executeQuery()) {
                return readOne(resultSet);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query user", exception);
        }
    }

    @Override
    public synchronized Optional<UserRecord> findByLoginCode(String loginCode) {
        return queryOne("select * from users where login_code = ?", loginCode);
    }

    @Override
    public synchronized Optional<UserRecord> findByEmail(String email) {
        return queryOne("select * from users where email = ?", email);
    }

    @Override
    public synchronized List<UserRecord> findAll() {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("select * from users order by id asc");
             ResultSet resultSet = statement.executeQuery()) {
            List<UserRecord> records = new ArrayList<>();
            while (resultSet.next()) {
                records.add(map(resultSet));
            }
            return records;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list users", exception);
        }
    }

    @Override
    public synchronized UserRecord incrementQuestionAnalysisCount(long id) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     update users
                     set question_analysis_count = question_analysis_count + 1
                     where id = ?
                     """)) {
            statement.setLong(1, id);
            if (statement.executeUpdate() == 0) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
            return findById(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update question analysis count", exception);
        }
    }

    public synchronized Optional<UserRecord> findByUsername(String username) {
        return queryOne("select * from users where username = ? order by id asc limit 1", username);
    }

    public synchronized Optional<UserRecord> updateEmail(long id, String email, boolean bound, boolean verified, String verifyCode) {
        String sql = """
                update users
                set email = ?, email_bound = ?, email_verified = ?, email_verify_code = ?
                where id = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            statement.setBoolean(2, bound);
            statement.setBoolean(3, verified);
            statement.setString(4, verifyCode);
            statement.setLong(5, id);
            if (statement.executeUpdate() == 0) {
                return Optional.empty();
            }
            return findById(id);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update user email", exception);
        }
    }

    private void initSchema() {
        String sql = """
                create table if not exists users (
                  id integer primary key,
                  username text not null,
                  password_hash text not null,
                  email text unique,
                  login_code text not null unique,
                  email_bound integer not null default 0,
                  email_verified integer not null default 0,
                  email_verify_code text,
                  question_analysis_count integer not null default 0
                )
                """;
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            ensureColumn(connection, "question_analysis_count", "integer not null default 0");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize user schema", exception);
        }
    }

    private void ensureColumn(Connection connection, String column, String definition) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("pragma table_info(users)");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                if (column.equalsIgnoreCase(resultSet.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("alter table users add column " + column + " " + definition);
        }
    }

    private Optional<UserRecord> queryOne(String sql, Object value) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (value instanceof Long longValue) {
                statement.setLong(1, longValue);
            } else {
                statement.setString(1, String.valueOf(value));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return readOne(resultSet);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query user", exception);
        }
    }

    private Optional<UserRecord> readOne(ResultSet resultSet) throws SQLException {
        if (!resultSet.next()) {
            return Optional.empty();
        }
        return Optional.of(map(resultSet));
    }

    private UserRecord map(ResultSet resultSet) throws SQLException {
        return new UserRecord(
                resultSet.getLong("id"),
                resultSet.getString("username"),
                resultSet.getString("password_hash"),
                resultSet.getString("email"),
                resultSet.getString("login_code"),
                resultSet.getBoolean("email_bound"),
                resultSet.getBoolean("email_verified"),
                resultSet.getString("email_verify_code"),
                resultSet.getLong("question_analysis_count")
        );
    }

    private void bind(PreparedStatement statement, UserRecord user) throws SQLException {
        statement.setLong(1, user.id());
        statement.setString(2, user.username());
        statement.setString(3, user.passwordHash());
        statement.setString(4, user.email());
        statement.setString(5, user.loginCode());
        statement.setBoolean(6, user.emailBound());
        statement.setBoolean(7, user.emailVerified());
        statement.setString(8, user.emailVerifyCode());
        statement.setLong(9, user.questionAnalysisCount());
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private Path resolveDbPath() {
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath.trim()).toAbsolutePath().normalize();
        }
        Path codeSource = codeSourcePath();
        if (codeSource != null && Files.isRegularFile(codeSource)) {
            return codeSource.getParent().resolve(DEFAULT_FILE_NAME).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.dir")).resolve(DEFAULT_FILE_NAME).toAbsolutePath().normalize();
    }

    private Path codeSourcePath() {
        try {
            return Path.of(ClarkAamsBackendApplication.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch (URISyntaxException | NullPointerException exception) {
            return null;
        }
    }
}
