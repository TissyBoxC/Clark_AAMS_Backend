package io.github.tissyboxc.clark_aams_backend.course;

import io.github.tissyboxc.clark_aams_backend.ClarkAamsBackendApplication;
import io.github.tissyboxc.clark_aams_backend.importers.dto.WeekPattern;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

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

/**
 * 课表持久化（P3-1）。
 *
 * <p>此前后端对课程是**完全无状态**的：导入结果直接返回给客户端，
 * 由客户端自己保存。这带来两个后果：
 * <ul>
 *   <li>换设备/清缓存后课表就没了</li>
 *   <li>Web 与 App 各自存一份，无法共享</li>
 * </ul>
 *
 * <p>本仓库把课程落到与用户同一个 SQLite 文件里，
 * 复用已验证过的连接方式，不额外引入数据库依赖。
 *
 * <p>周次同时存 {@code week_ranges}（JSON）与 {@code weeks}（逗号分隔）：
 * 前者是真相源，后者保留是为了让直接读库的运维查询也看得懂。
 */
@Repository
public class CourseRepository {

    private static final String DEFAULT_FILE_NAME = "clark-aams.db";

    private final String configuredPath;
    private Path dbPath;
    private String jdbcUrl;

    public CourseRepository(@Value("${clark-aams.user.db-path:}") String configuredPath) {
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
            throw new UncheckedIOException("Failed to create course db directory", exception);
        }
        jdbcUrl = "jdbc:sqlite:" + dbPath;
        initSchema();
    }

    private void initSchema() {
        String sql = """
                create table if not exists courses(
                  id integer primary key autoincrement,
                  owner_id integer not null,
                  name text not null,
                  day_of_week integer not null,
                  start_lesson integer not null,
                  end_lesson integer not null,
                  location text not null default '',
                  teacher text not null default '',
                  raw_time text not null default '',
                  week_ranges text not null default '[]',
                  week_rule text not null default 'ALL',
                  start_week integer not null default 1,
                  end_week integer not null default 1,
                  color_value integer,
                  sort_order integer not null default 0,
                  created_at integer not null default 0,
                  updated_at integer not null default 0
                )
                """;
        String indexSql = """
                create index if not exists idx_courses_owner on courses(owner_id)
                """;
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            statement.execute(indexSql);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize courses table", exception);
        }
    }

    /** 某用户的全部课程，按天、起始节次排序。 */
    public synchronized List<CourseRecord> findByOwner(long ownerId) {
        String sql = """
                select id, owner_id, name, day_of_week, start_lesson, end_lesson,
                       location, teacher, raw_time, week_ranges, week_rule,
                       start_week, end_week, color_value, sort_order
                from courses
                where owner_id = ?
                order by day_of_week asc, start_lesson asc, sort_order asc, id asc
                """;
        List<CourseRecord> records = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(map(resultSet));
                }
            }
            return records;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query courses", exception);
        }
    }

    /**
     * 用给定课程整体替换某用户的课表。
     *
     * <p>导入是一次性的整体动作，逐条 diff 没有意义且容易留下残影，
     * 因此在一个事务里「先删后插」。
     */
    public synchronized List<CourseRecord> replaceAll(long ownerId, List<CourseRecord> courses) {
        long now = System.currentTimeMillis();
        String deleteSql = "delete from courses where owner_id = ?";
        String insertSql = """
                insert into courses(owner_id, name, day_of_week, start_lesson, end_lesson,
                                    location, teacher, raw_time, week_ranges, week_rule,
                                    start_week, end_week, color_value, sort_order,
                                    created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(deleteSql)) {
                    delete.setLong(1, ownerId);
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    int order = 0;
                    for (CourseRecord course : courses) {
                        insert.setLong(1, ownerId);
                        insert.setString(2, course.name());
                        insert.setInt(3, course.dayOfWeek());
                        insert.setInt(4, course.startLesson());
                        insert.setInt(5, course.endLesson());
                        insert.setString(6, course.location());
                        insert.setString(7, course.teacher());
                        insert.setString(8, course.rawTime());
                        insert.setString(9, course.weekRangesJson());
                        insert.setString(10, course.weekRule());
                        insert.setInt(11, course.startWeek());
                        insert.setInt(12, course.endWeek());
                        if (course.colorValue() == null) {
                            insert.setNull(13, java.sql.Types.INTEGER);
                        } else {
                            insert.setInt(13, course.colorValue());
                        }
                        insert.setInt(14, order++);
                        insert.setLong(15, now);
                        insert.setLong(16, now);
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to replace courses", exception);
        }
        return findByOwner(ownerId);
    }

    /** 新增一门课，返回带 id 的记录。 */
    public synchronized CourseRecord insert(long ownerId, CourseRecord course) {
        long now = System.currentTimeMillis();
        String sql = """
                insert into courses(owner_id, name, day_of_week, start_lesson, end_lesson,
                                    location, teacher, raw_time, week_ranges, week_rule,
                                    start_week, end_week, color_value, sort_order,
                                    created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, ownerId);
            statement.setString(2, course.name());
            statement.setInt(3, course.dayOfWeek());
            statement.setInt(4, course.startLesson());
            statement.setInt(5, course.endLesson());
            statement.setString(6, course.location());
            statement.setString(7, course.teacher());
            statement.setString(8, course.rawTime());
            statement.setString(9, course.weekRangesJson());
            statement.setString(10, course.weekRule());
            statement.setInt(11, course.startWeek());
            statement.setInt(12, course.endWeek());
            if (course.colorValue() == null) {
                statement.setNull(13, java.sql.Types.INTEGER);
            } else {
                statement.setInt(13, course.colorValue());
            }
            statement.setLong(14, now);
            statement.setLong(15, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : 0L;
                return course.withId(id);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to insert course", exception);
        }
    }

    /** 更新一门课；不存在或不属于该用户时返回 false。 */
    public synchronized boolean update(long ownerId, long courseId, CourseRecord course) {
        String sql = """
                update courses set
                  name = ?, day_of_week = ?, start_lesson = ?, end_lesson = ?,
                  location = ?, teacher = ?, raw_time = ?, week_ranges = ?, week_rule = ?,
                  start_week = ?, end_week = ?, color_value = ?, updated_at = ?
                where id = ? and owner_id = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, course.name());
            statement.setInt(2, course.dayOfWeek());
            statement.setInt(3, course.startLesson());
            statement.setInt(4, course.endLesson());
            statement.setString(5, course.location());
            statement.setString(6, course.teacher());
            statement.setString(7, course.rawTime());
            statement.setString(8, course.weekRangesJson());
            statement.setString(9, course.weekRule());
            statement.setInt(10, course.startWeek());
            statement.setInt(11, course.endWeek());
            if (course.colorValue() == null) {
                statement.setNull(12, java.sql.Types.INTEGER);
            } else {
                statement.setInt(12, course.colorValue());
            }
            statement.setLong(13, System.currentTimeMillis());
            statement.setLong(14, courseId);
            statement.setLong(15, ownerId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update course", exception);
        }
    }

    /** 删除一门课；不存在或不属于该用户时返回 false。 */
    public synchronized boolean delete(long ownerId, long courseId) {
        String sql = "delete from courses where id = ? and owner_id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, courseId);
            statement.setLong(2, ownerId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete course", exception);
        }
    }

    /** 清空某用户课表，返回删除条数。 */
    public synchronized int deleteAll(long ownerId) {
        String sql = "delete from courses where owner_id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerId);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to clear courses", exception);
        }
    }

    private CourseRecord map(ResultSet resultSet) throws SQLException {
        int rawColor = resultSet.getInt("color_value");
        // wasNull() 只反映「最近一次 getXxx 调用」，必须紧跟在读取 color_value 之后，
        // 否则会误用它前面某一列的空状态。
        Integer colorValue = resultSet.wasNull() ? null : rawColor;
        return new CourseRecord(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getInt("day_of_week"),
                resultSet.getInt("start_lesson"),
                resultSet.getInt("end_lesson"),
                resultSet.getString("location"),
                resultSet.getString("teacher"),
                resultSet.getString("raw_time"),
                resultSet.getString("week_ranges"),
                resultSet.getString("week_rule"),
                resultSet.getInt("start_week"),
                resultSet.getInt("end_week"),
                colorValue
        );
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

    /** 课程记录（不含 owner，避免泄漏内部归属）。 */
    public record CourseRecord(
            long id,
            String name,
            int dayOfWeek,
            int startLesson,
            int endLesson,
            String location,
            String teacher,
            String rawTime,
            String weekRangesJson,
            String weekRule,
            int startWeek,
            int endWeek,
            Integer colorValue
    ) {
        public CourseRecord withId(long newId) {
            return new CourseRecord(newId, name, dayOfWeek, startLesson, endLesson,
                    location, teacher, rawTime, weekRangesJson, weekRule, startWeek, endWeek, colorValue);
        }

        /** 该课程的周次规则（从存储的字符串还原）。 */
        public WeekPattern.Rule rule() {
            try {
                return WeekPattern.Rule.valueOf(weekRule);
            } catch (IllegalArgumentException exception) {
                return WeekPattern.Rule.ALL;
            }
        }
    }
}
