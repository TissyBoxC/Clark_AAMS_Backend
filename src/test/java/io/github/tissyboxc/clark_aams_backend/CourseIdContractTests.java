package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.course.CourseRepository;
import io.github.tissyboxc.clark_aams_backend.course.CourseService;
import io.github.tissyboxc.clark_aams_backend.importers.dto.CourseDto;
import io.github.tissyboxc.clark_aams_backend.importers.dto.WeekPattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 课程接口必须回传 `id`（P3-3）。
 *
 * <p>P3-3 要给 Web 补上「手工新增 / 编辑课程」，做端到端验证时发现：
 * `POST /api/v1/courses/schedule` 返回的课程里**没有 id**，
 * `GET` 列表同样没有。
 *
 * <p>而 `PUT /{courseId}` 与 `DELETE /{courseId}` 都要用 id。
 * 客户端刚建完一门课，无从得知它的 id，于是：
 *
 * <ul>
 *   <li>新建的课删不掉——除非重新拉一遍列表并按顺序猜</li>
 *   <li>Web 之前确实是按「数组下标 + 1」猜的，删错课只是时间问题</li>
 * </ul>
 *
 * <p>这不是新功能缺失，是一个**既有的契约缺口**：
 * 服务端自己分配了 id，却不告诉客户端。
 */
class CourseIdContractTests {

    @TempDir
    Path tempDir;

    private CourseService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        CourseRepository repository = new CourseRepository(tempDir.resolve("courses.db").toString());
        repository.initialize();
        service = new CourseService(repository, objectMapper);
    }

    private static CourseDto course(String name) {
        return CourseDto.of(
                name, 1, 1, 2,
                "A101", "张老师", "",
                new WeekPattern(List.of(new int[]{1, 16}), WeekPattern.Rule.ALL)
        );
    }

    @Test
    @DisplayName("新建课程后，返回结果里带有可用的 id")
    void createReturnsId() {
        CourseDto created = service.create(1L, course("高等数学"));

        assertNotNull(created, "创建结果不应为空");
        long id = created.id();
        assertTrue(id > 0, "新建课程应带回一个有效的 id，实际 " + id);
    }

    @Test
    @DisplayName("返回的 id 可以直接用于删除")
    void returnedIdIsUsableForDelete() {
        CourseDto created = service.create(1L, course("线性代数"));
        long id = created.id();

        // 删掉它，然后列表应为空——证明这个 id 是真的、能用的
        service.delete(1L, id);
        assertEquals(0, service.list(1L).size(), "用返回的 id 应能删掉刚建的课");
    }

    @Test
    @DisplayName("返回的 id 可以直接用于更新")
    void returnedIdIsUsableForUpdate() {
        CourseDto created = service.create(1L, course("大学物理"));
        long id = created.id();

        service.update(1L, id, course("大学物理（改）"));

        List<CourseDto> courses = service.list(1L);
        assertEquals(1, courses.size());
        assertEquals("大学物理（改）", courses.get(0).name());
        assertEquals(id, courses.get(0).id(), "列表里的 id 应与创建时返回的一致");
    }

    @Test
    @DisplayName("列表返回的每门课都带 id，且互不相同")
    void listReturnsDistinctIds() {
        long first = service.create(1L, course("课程A")).id();
        long second = service.create(1L, course("课程B")).id();

        List<CourseDto> courses = service.list(1L);
        assertEquals(2, courses.size());
        assertTrue(courses.stream().allMatch(dto -> dto.id() > 0), "每门课都应有 id");
        assertEquals(2, courses.stream().map(CourseDto::id).distinct().count(), "id 必须互不相同");
        assertTrue(List.of(first, second).containsAll(courses.stream().map(CourseDto::id).toList()));
    }

    @Test
    @DisplayName("不同用户的课程 id 不会串台")
    void idsAreScopedPerUser() {
        CourseDto mine = service.create(1L, course("我的课"));
        CourseDto theirs = service.create(2L, course("别人的课"));

        assertTrue(mine.id() > 0);
        assertTrue(theirs.id() > 0);

        assertEquals(1, service.list(1L).size());
        assertEquals(mine.id(), service.list(1L).get(0).id());
    }
}
