package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.course.CourseUpsertRequest;
import io.github.tissyboxc.clark_aams_backend.importers.dto.CourseDto;
import io.github.tissyboxc.clark_aams_backend.importers.dto.WeekPattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 课表写请求的周次解析（P3-1）。
 *
 * <p>写接口只接受「真相源」字段，派生字段统一在服务端算，
 * 因此这里的解析规则必须锁死，否则 App / Web 提交同一份数据
 * 会得到不同结果。
 */
class CourseUpsertRequestTests {

    private CourseUpsertRequest request(
            List<Integer> weeks,
            List<List<Integer>> weekRanges,
            String weekRule
    ) {
        return new CourseUpsertRequest(
                "课", 1, 1, 2, "A101", "张老师", "",
                weeks, weekRanges, weekRule
        );
    }

    @Test
    @DisplayName("weekRanges 紧凑形态优先于 weeks")
    void rangesTakePrecedence() {
        WeekPattern pattern = request(
                List.of(1, 2, 3),
                List.of(List.of(1, 16)),
                "ALL"
        ).resolvePattern();

        assertEquals(1, pattern.ranges().size());
        assertEquals(16, pattern.expand().size(), "应使用 weekRanges 而非 weeks");
    }

    @Test
    @DisplayName("只有 weeks 时从离散周次推导单双周")
    void derivesFromWeeksOnly() {
        WeekPattern odd = request(List.of(1, 3, 5, 7), null, null).resolvePattern();
        assertEquals(WeekPattern.Rule.ODD, odd.rule());
        assertEquals(List.of(1, 3, 5, 7), odd.expand());

        WeekPattern even = request(List.of(2, 4, 6, 8), null, null).resolvePattern();
        assertEquals(WeekPattern.Rule.EVEN, even.rule());
        assertEquals(List.of(2, 4, 6, 8), even.expand());
    }

    @Test
    @DisplayName("空 weekRule 视为 ALL，大小写与空白都容忍")
    void toleratesRuleSpelling() {
        assertEquals(WeekPattern.Rule.ALL, request(null, List.of(List.of(1, 8)), null).resolvePattern().rule());
        assertEquals(WeekPattern.Rule.ODD, request(null, List.of(List.of(1, 9)), "odd").resolvePattern().rule());
        assertEquals(WeekPattern.Rule.EVEN, request(null, List.of(List.of(2, 10)), "  Even  ").resolvePattern().rule());
    }

    @Test
    @DisplayName("无法识别的 weekRule 退化为 ALL 而不是报错")
    void unknownRuleFallsBackToAll() {
        assertEquals(WeekPattern.Rule.ALL, request(null, List.of(List.of(1, 8)), "WEEKLY").resolvePattern().rule());
    }

    @Test
    @DisplayName("周次全空得到空 pattern，不产生幻觉周次")
    void emptyInputYieldsEmptyPattern() {
        WeekPattern empty = request(null, null, null).resolvePattern();
        assertTrue(empty.ranges().isEmpty());
        assertTrue(empty.expand().isEmpty());
        assertEquals(WeekPattern.Rule.ALL, empty.rule());
    }

    @Test
    @DisplayName("畸形 weekRanges 项被跳过而不是抛异常")
    void skipsMalformedRanges() {
        WeekPattern pattern = request(
                null,
                List.of(List.of(1, 8), List.of(5), List.of()),
                "ALL"
        ).resolvePattern();

        assertEquals(1, pattern.ranges().size(), "只有完整的两元组才被采纳");
        assertEquals(8, pattern.expand().size());
    }

    @Test
    @DisplayName("转成 CourseDto 后派生字段自洽")
    void toCourseDtoIsSelfConsistent() {
        CourseDto dto = request(null, List.of(List.of(1, 9)), "ODD").toCourseDto();

        assertEquals(WeekPattern.Rule.ODD, dto.weekRule());
        assertEquals(1, dto.startWeek());
        assertEquals(9, dto.endWeek());
        assertEquals(List.of(1, 3, 5, 7, 9), dto.weeks());
    }

    @Test
    @DisplayName("空字符串字段被规整，不会写入 null")
    void nullStringsBecomeEmpty() {
        CourseUpsertRequest bare = new CourseUpsertRequest(
                "课", 1, 1, 2, null, null, null, List.of(1, 2), null, null
        );
        CourseDto dto = bare.toCourseDto();

        assertEquals("", dto.location());
        assertEquals("", dto.teacher());
        assertEquals("", dto.rawTime());
    }
}
