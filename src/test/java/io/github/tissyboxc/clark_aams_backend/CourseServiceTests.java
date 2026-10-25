package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.course.CourseRepository;
import io.github.tissyboxc.clark_aams_backend.course.CourseService;
import io.github.tissyboxc.clark_aams_backend.importers.dto.CourseDto;
import io.github.tissyboxc.clark_aams_backend.importers.dto.WeekPattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 课表资源的持久化与隔离（P3-1）。
 *
 * <p>用临时 SQLite 文件跑真实读写，不打桩——
 * 这里最容易出错的是 SQL 绑定与周次序列化，桩测不出来。
 */
class CourseServiceTests {

    @TempDir
    Path tempDir;

    private CourseService service;

    @BeforeEach
    void setUp() {
        CourseRepository repository = new CourseRepository(tempDir.resolve("test.db").toString());
        repository.initialize();
        service = new CourseService(repository, new tools.jackson.databind.ObjectMapper());
    }

    private CourseDto course(String name, int day, int startLesson, int endLesson, List<int[]> ranges, WeekPattern.Rule rule) {
        WeekPattern pattern = new WeekPattern(ranges, rule);
        List<Integer> weeks = pattern.expand();
        return new CourseDto(
                name,
                weeks.isEmpty() ? 1 : weeks.get(0),
                weeks.isEmpty() ? 1 : weeks.get(weeks.size() - 1),
                day,
                startLesson,
                endLesson,
                "A101",
                "张老师",
                weeks,
                "",
                ranges,
                rule
        );
    }

    @Test
    @DisplayName("导入结果落库后可按用户读回，周次往返无损")
    void replaceAndListRoundTrip() {
        long owner = 1L;
        service.replaceFromImport(owner, List.of(
                course("高等数学", 1, 1, 2, List.of(new int[]{1, 16}), WeekPattern.Rule.ALL),
                course("单周实验", 3, 5, 6, List.of(new int[]{1, 9}), WeekPattern.Rule.ODD)
        ));

        List<CourseDto> saved = service.list(owner);
        assertEquals(2, saved.size());

        CourseDto math = saved.stream().filter(c -> c.name().equals("高等数学")).findFirst().orElseThrow();
        assertEquals(1, math.dayOfWeek());
        assertEquals(16, math.weeks().size());
        assertEquals(WeekPattern.Rule.ALL, math.weekRule());

        CourseDto odd = saved.stream().filter(c -> c.name().equals("单周实验")).findFirst().orElseThrow();
        assertEquals(WeekPattern.Rule.ODD, odd.weekRule());
        assertEquals(List.of(1, 3, 5, 7, 9), odd.weeks(), "单周应展开为奇数周");
        assertTrue(odd.weekRanges().size() == 1 && odd.weekRanges().get(0)[1] == 9);
    }

    @Test
    @DisplayName("不同用户的课表互相隔离")
    void ownersAreIsolated() {
        service.replaceFromImport(1L, List.of(course("用户1的课", 1, 1, 2, List.of(new int[]{1, 8}), WeekPattern.Rule.ALL)));
        service.replaceFromImport(2L, List.of(
                course("用户2的课A", 2, 1, 2, List.of(new int[]{1, 8}), WeekPattern.Rule.ALL),
                course("用户2的课B", 3, 3, 4, List.of(new int[]{1, 8}), WeekPattern.Rule.ALL)
        ));

        assertEquals(1, service.list(1L).size());
        assertEquals(2, service.list(2L).size());
        assertEquals("用户1的课", service.list(1L).get(0).name());
    }

    @Test
    @DisplayName("再次导入是覆盖而非累加")
    void replaceOverwrites() {
        service.replaceFromImport(1L, List.of(
                course("旧课A", 1, 1, 2, List.of(new int[]{1, 8}), WeekPattern.Rule.ALL),
                course("旧课B", 2, 1, 2, List.of(new int[]{1, 8}), WeekPattern.Rule.ALL)
        ));

        service.replaceFromImport(1L, List.of(
                course("新课", 3, 1, 2, List.of(new int[]{1, 8}), WeekPattern.Rule.ALL)
        ));

        List<CourseDto> after = service.list(1L);
        assertEquals(1, after.size(), "旧记录不应残留");
        assertEquals("新课", after.get(0).name());
    }

    @Test
    @DisplayName("手工新增课程")
    void createCourse() {
        CourseDto created = service.create(1L, course("手工课", 4, 3, 4, List.of(new int[]{2, 10}), WeekPattern.Rule.ALL));

        assertTrue(created.name().equals("手工课"));
        assertEquals(1, service.list(1L).size());
    }

    @Test
    @DisplayName("课程名称不能为空")
    void rejectsBlankName() {
        CourseDto blank = course("  ", 1, 1, 2, List.of(new int[]{1, 8}), WeekPattern.Rule.ALL);
        assertThrows(io.github.tissyboxc.clark_aams_backend.common.BusinessException.class,
                () -> service.create(1L, blank));
    }

    @Test
    @DisplayName("星期超出 1-7 被拒绝")
    void rejectsInvalidDay() {
        CourseDto badDay = course("坏星期", 8, 1, 2, List.of(new int[]{1, 8}), WeekPattern.Rule.ALL);
        assertThrows(io.github.tissyboxc.clark_aams_backend.common.BusinessException.class,
                () -> service.create(1L, badDay));
    }

    @Test
    @DisplayName("节次范围必须有效")
    void rejectsInvalidLessons() {
        CourseDto badLessons = course("坏节次", 1, 5, 3, List.of(new int[]{1, 8}), WeekPattern.Rule.ALL);
        assertThrows(io.github.tissyboxc.clark_aams_backend.common.BusinessException.class,
                () -> service.create(1L, badLessons));
    }

    @Test
    @DisplayName("删除不存在的课程抛 404")
    void deleteMissingThrows() {
        assertThrows(io.github.tissyboxc.clark_aams_backend.common.BusinessException.class,
                () -> service.delete(1L, 9999L));
    }

    @Test
    @DisplayName("清空只影响该用户")
    void clearOnlyAffectsOwner() {
        service.replaceFromImport(1L, List.of(course("A", 1, 1, 2, List.of(new int[]{1, 8}), WeekPattern.Rule.ALL)));
        service.replaceFromImport(2L, List.of(course("B", 1, 1, 2, List.of(new int[]{1, 8}), WeekPattern.Rule.ALL)));

        assertEquals(1, service.clear(1L));
        assertEquals(0, service.list(1L).size());
        assertEquals(1, service.list(2L).size(), "另一个用户的课表不应受影响");
    }

    @Test
    @DisplayName("空课表返回空列表而不是报错")
    void emptyOwnerReturnsEmptyList() {
        assertTrue(service.list(999L).isEmpty());
    }
}
