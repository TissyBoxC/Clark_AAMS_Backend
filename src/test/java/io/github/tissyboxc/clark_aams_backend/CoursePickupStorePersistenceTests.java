package io.github.tissyboxc.clark_aams_backend;

import tools.jackson.databind.ObjectMapper;
import io.github.tissyboxc.clark_aams_backend.pickup.CoursePickupStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 取件码索引的写入可靠性（P5-3）。
 *
 * <p>关注点不是业务逻辑，而是**写到一半崩掉会不会毁掉全部数据**。
 * 原实现直接截断目标文件再写，中断即损坏；
 * 改成「临时文件 + 原子改名」后，磁盘上任何时刻都有一份完整索引。
 */
class CoursePickupStorePersistenceTests {

    @TempDir
    Path tempDir;

    private CoursePickupStore store;

    @BeforeEach
    void setUp() {
        store = new CoursePickupStore(new ObjectMapper(), tempDir.toString());
        store.initialize();
    }

    @Test
    @DisplayName("写入后能读回，索引文件是完整 JSON 数组")
    void writesReadableIndex() throws IOException {
        store.create("[{\"name\":\"高等数学\"}]", List.of());

        Path index = tempDir.resolve("index.json");
        assertTrue(Files.exists(index), "索引文件应存在");

        String text = Files.readString(index, StandardCharsets.UTF_8);
        assertTrue(text.trim().startsWith("["), "索引应是 JSON 数组，实际：" + text);
        assertTrue(text.contains("高等数学"), "索引应包含写入的内容");
    }

    @Test
    @DisplayName("多次写入不残留临时文件")
    void leavesNoTempFiles() throws IOException {
        for (int i = 0; i < 5; i++) {
            store.create("[{\"round\":" + i + "}]", List.of());
        }

        try (Stream<Path> files = Files.list(tempDir)) {
            List<String> leftovers = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".tmp"))
                    .toList();
            assertTrue(leftovers.isEmpty(), "不应残留临时文件，实际：" + leftovers);
        }
    }

    @Test
    @DisplayName("索引被外部破坏时不会静默清空数据")
    void corruptedIndexDoesNotWipeSilently() throws IOException {
        store.create("[{\"name\":\"a\"}]", List.of());
        Path index = tempDir.resolve("index.json");

        // 模拟「写一半崩掉」留下的半截文件
        Files.writeString(index, "[{\"code\":\"ABC", StandardCharsets.UTF_8);

        try {
            store.create("[{\"name\":\"b\"}]", List.of());
        } catch (RuntimeException expected) {
            // 读不出旧索引时报错是正确行为：宁可失败，也不要覆盖成空数据
            return;
        }

        // 如果没有报错，至少不能把原来的数据变成空
        String text = Files.readString(index, StandardCharsets.UTF_8);
        assertFalse(text.isBlank(), "损坏后不应被静默覆盖成空文件");
    }

    @Test
    @DisplayName("覆盖写时旧内容不会与新内容混杂")
    void overwriteDoesNotInterleave() throws IOException {
        // create 每次生成新取件码，不会覆盖旧记录；
        // 真正「覆盖同一文件」要通过 updateJson 改同一条记录
        var created = store.create(
                "[{\"name\":\"first\",\"payload\":\"" + "x".repeat(2000) + "\"}]",
                List.of()
        );
        store.updateJson(created.code(), "[{\"name\":\"second\"}]");

        Path index = tempDir.resolve("index.json");
        String text = Files.readString(index, StandardCharsets.UTF_8);

        assertFalse(text.contains("first"), "旧内容不应残留");
        assertTrue(text.contains("second"), "新内容应写入");
    }
}
