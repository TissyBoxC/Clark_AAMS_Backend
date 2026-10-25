package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.pickup.CoursePickupStore;
import io.github.tissyboxc.clark_aams_backend.user.SQLiteUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据目录外置（P5-2）。
 *
 * <p>关注「显式配置能不能真正接管数据落点」。环境变量
 * {@code CLARK_AAMS_DATA_DIR} 无法在测试进程内安全地设置/取消，
 * 因此这里覆盖的是优先级更高的配置项路径；
 * 环境变量分支与之走同一段解析逻辑，靠代码复核而非单测。
 */
class DataDirectoryTests {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("用户库落到显式配置的路径")
    void userDatabaseHonoursConfiguredPath() {
        Path dbPath = tempDir.resolve("custom/users.db");
        SQLiteUserRepository repository = new SQLiteUserRepository(dbPath.toString());
        repository.initialize();

        assertTrue(Files.exists(dbPath), "数据库应创建在配置的路径：" + dbPath);
    }

    @Test
    @DisplayName("用户库自动创建不存在的父目录")
    void userDatabaseCreatesParentDirectories() {
        Path dbPath = tempDir.resolve("deep/nested/dir/users.db");
        SQLiteUserRepository repository = new SQLiteUserRepository(dbPath.toString());
        repository.initialize();

        assertTrue(Files.exists(dbPath.getParent()), "父目录应被创建");
        assertTrue(Files.exists(dbPath), "数据库文件应存在");
    }

    @Test
    @DisplayName("取件码目录落到显式配置的路径")
    void pickupStorageHonoursConfiguredPath() {
        Path storage = tempDir.resolve("custom-pickups");
        CoursePickupStore store = new CoursePickupStore(new ObjectMapper(), storage.toString());
        store.initialize();

        assertTrue(Files.exists(storage.resolve("index.json")), "索引应创建在配置目录：" + storage);
    }

    @Test
    @DisplayName("两个数据源可以放在同一父目录下，便于整体备份")
    void dataSourcesCanShareParent() throws IOException {
        Path dataDir = tempDir.resolve("data");
        SQLiteUserRepository repository = new SQLiteUserRepository(dataDir.resolve("users.db").toString());
        repository.initialize();
        CoursePickupStore store = new CoursePickupStore(new ObjectMapper(), dataDir.resolve("course-pickups").toString());
        store.initialize();

        try (Stream<Path> files = Files.list(dataDir)) {
            var names = files.map(path -> path.getFileName().toString()).sorted().toList();
            assertTrue(names.contains("users.db"), "同一目录下应有 users.db，实际：" + names);
            assertTrue(names.contains("course-pickups"), "同一目录下应有 course-pickups，实际：" + names);
        }
        assertEquals(2, Files.list(dataDir).count(), "data 目录应只有这两项数据");
    }
}
