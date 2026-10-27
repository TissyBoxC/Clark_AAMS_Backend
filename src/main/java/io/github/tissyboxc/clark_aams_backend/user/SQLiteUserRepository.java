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
    private static final String DATA_DIR_ENV = "CLARK_AAMS_DATA_DIR";

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
             ResultSet resultSet = statement.executeQuery("select coalesce((select seq from sqlite_sequence where name = 'users'), 0) + 1 as next_id")) {
            return resultSet.next() ? resultSet.getLong("next_id") : 1L;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to allocate next user id", exception);
        }
    }

    /**
     * 原子地分配 id 并插入（P5-1）。
     *
     * <p>原实现是「先 nextId() 再 save()」，两条语句之间没有原子性：
     * 并发注册会拿到同一个 id，后写的覆盖先写的。
     * 这里让数据库自增分配 id，「取号」与「插入」在同一条 SQL 内完成。
     */
    @Override
    public synchronized UserRecord insert(UserRecord user) {
        String sql = """
                insert into users(username, password_hash, email, login_code, email_bound, email_verified, email_verify_code, question_analysis_count, login_code_issued_at, role)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, user.username());
            statement.setString(2, user.passwordHash());
            statement.setString(3, user.email());
            statement.setString(4, user.loginCode());
            statement.setBoolean(5, user.emailBound());
            statement.setBoolean(6, user.emailVerified());
            statement.setString(7, user.emailVerifyCode());
            statement.setLong(8, user.questionAnalysisCount());
            statement.setLong(9, user.loginCodeIssuedAt());
            statement.setString(10, user.effectiveRole());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : user.id();
                return new UserRecord(
                        id,
                        user.username(),
                        user.passwordHash(),
                        user.email(),
                        user.loginCode(),
                        user.emailBound(),
                        user.emailVerified(),
                        user.emailVerifyCode(),
                        user.questionAnalysisCount(),
                        user.loginCodeIssuedAt(),
                        // 必须带上 role：10 参数构造器会把角色默认成 user，
                        // 于是「库里存的是 admin，返回的对象却是 user」——
                        // 管理员刚建好就被当成普通用户，进不去管理台。
                        user.effectiveRole()
                );
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to insert user", exception);
        }
    }

    @Override
    public synchronized UserRecord save(UserRecord user) {
        String sql = """
                insert into users(id, username, password_hash, email, login_code, email_bound, email_verified, email_verify_code, question_analysis_count, login_code_issued_at, role)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                  username = excluded.username,
                  password_hash = excluded.password_hash,
                  email = excluded.email,
                  login_code = excluded.login_code,
                  email_bound = excluded.email_bound,
                  email_verified = excluded.email_verified,
                  email_verify_code = excluded.email_verify_code,
                  question_analysis_count = excluded.question_analysis_count,
                  login_code_issued_at = excluded.login_code_issued_at,
                  role = excluded.role
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
    public synchronized UserRecord rotateLoginCode(long id, String newLoginCode) {
        String sql = "update users set login_code = ?, login_code_issued_at = ? where id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newLoginCode);
            statement.setLong(2, System.currentTimeMillis());
            statement.setLong(3, id);
            int updated = statement.executeUpdate();
            if (updated == 0) {
                throw new IllegalStateException("User not found: " + id);
            }
            return findById(id).orElseThrow();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to rotate login code", exception);
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
    public synchronized Optional<UserRecord> findByUsername(String username) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "select * from users where username = ?")) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                return readOne(resultSet);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query user by username", exception);
        }
    }

    @Override
    public synchronized void updatePasswordHash(long id, String passwordHash) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "update users set password_hash = ? where id = ?")) {
            statement.setString(1, passwordHash);
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update password hash", exception);
        }
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

    private void initSchema() {
        String sql = """
                create table if not exists users (
                  id integer primary key autoincrement,
                  username text not null,
                  password_hash text not null,
                  email text unique,
                  login_code text not null unique,
                  email_bound integer not null default 0,
                  email_verified integer not null default 0,
                  email_verify_code text,
                  question_analysis_count integer not null default 0,
                  login_code_issued_at integer not null default 0,
                  role text not null default 'user'
                )
                """;
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            ensureColumn(connection, "question_analysis_count", "integer not null default 0");
            // 历史数据的登录码签发时间补 0（视为不过期），不做强制登出
            ensureColumn(connection, "login_code_issued_at", "integer not null default 0");
            // P1-1：合并账号表后，角色成为 users 表的一个字段。
            // 历史数据全部默认为 user —— 管理员由迁移流程显式提升。
            ensureColumn(connection, "role", "text not null default 'user'");
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
                resultSet.getLong("question_analysis_count"),
                resultSet.getLong("login_code_issued_at"),
                resultSet.getString("role")
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
        statement.setLong(10, user.loginCodeIssuedAt());
        statement.setString(11, user.effectiveRole());
    }

    /**
     * 建立连接并应用并发相关设置（P5-1）。
     *
     * <p>原实现直接返回连接，SQLite 默认 busy timeout 为 0，
     * 并发写入时第二个连接会立刻抛 {@code SQLITE_BUSY}（database is locked）。
     * 实测 8 线程并发插入 7 个失败——即真实场景下并发注册会莫名失败。
     *
     * <p>两项设置：
     * <ul>
     *   <li>{@code busy_timeout}：拿不到写锁时等待而不是立刻失败</li>
     *   <li>{@code journal_mode=WAL}：读写不互相阻塞，写入串行但读可并发</li>
     * </ul>
     */
    private Connection connection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("pragma busy_timeout = 5000");
            statement.execute("pragma journal_mode = WAL");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
        return connection;
    }

    /**
     * 解析数据库文件位置（P5-2）。
     *
     * <p>优先级：
     * <ol>
     *   <li>配置项 {@code clark-aams.user.db-path}（显式指定，最高优先级）</li>
     *   <li>环境变量 {@value #DATA_DIR_ENV}：数据目录，适用于容器与桌面打包分发</li>
     *   <li>jar 同级目录（历史行为，保持向后兼容）</li>
     *   <li>当前工作目录</li>
     * </ol>
     *
     * <p>第 2 项是 P5-2 新增的。原本只有「jar 同级」一种默认行为，
     * 这在两种常见部署下是错的：
     * <ul>
     *   <li>容器/只读镜像：jar 所在目录不可写，启动即失败</li>
     *   <li>桌面安装包升级：jar 被替换，数据文件跟着丢</li>
     * </ul>
     * 有了数据目录变量，运维可以把数据放在任意持久化位置。
     */
    private Path resolveDbPath() {
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath.trim()).toAbsolutePath().normalize();
        }
        String dataDir = System.getenv(DATA_DIR_ENV);
        if (dataDir != null && !dataDir.isBlank()) {
            return Path.of(dataDir.trim())
                    .resolve(DEFAULT_FILE_NAME)
                    .toAbsolutePath()
                    .normalize();
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
