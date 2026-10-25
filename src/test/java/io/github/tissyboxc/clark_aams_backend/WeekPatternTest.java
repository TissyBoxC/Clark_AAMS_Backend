package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.importers.dto.CourseDto;
import io.github.tissyboxc.clark_aams_backend.importers.dto.WeekPattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 周次规则（唯一真相源）的行为约定。
 *
 * 这些用例锁死「区间 + 单双周」不会退化成「首末周」，即 C1/C3 的回归防线。
 */
class WeekPatternTest {

    @Test
    @DisplayName("从离散周次反推区间：不连续段保持独立")
    void fromWeeksKeepsGaps() {
        WeekPattern pattern = WeekPattern.fromWeeks(
                List.of(1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16));
        assertEquals(2, pattern.ranges().size(), "应为 1-3 与 5-16 两段");
        assertEquals(1, pattern.ranges().get(0)[0]);
        assertEquals(3, pattern.ranges().get(0)[1]);
        assertEquals(5, pattern.ranges().get(1)[0]);
        assertEquals(16, pattern.ranges().get(1)[1]);
        assertEquals(WeekPattern.Rule.ALL, pattern.rule());
    }

    @Test
    @DisplayName("全奇数识别为单周，展开仍为奇数")
    void oddRule() {
        WeekPattern pattern = WeekPattern.fromWeeks(List.of(1, 3, 5, 7));
        assertEquals(WeekPattern.Rule.ODD, pattern.rule());
        assertEquals(List.of(1, 3, 5, 7), pattern.expand());
    }

    @Test
    @DisplayName("单周区间应合并成一段，展示为 1-7周(单) 而非 1,3,5,7周(单)")
    void oddRuleMergesIntoOneRange() {
        WeekPattern pattern = WeekPattern.fromWeeks(List.of(1, 3, 5, 7));
        assertEquals(1, pattern.ranges().size(), "单周应合并为一个区间");
        assertEquals(1, pattern.ranges().get(0)[0]);
        assertEquals(7, pattern.ranges().get(0)[1]);
        assertEquals("1-7周(单)", pattern.toText());
    }

    @Test
    @DisplayName("双周区间应合并成一段")
    void evenRuleMergesIntoOneRange() {
        WeekPattern pattern = WeekPattern.fromWeeks(List.of(2, 4, 6, 8));
        assertEquals(1, pattern.ranges().size());
        assertEquals(2, pattern.ranges().get(0)[0]);
        assertEquals(8, pattern.ranges().get(0)[1]);
        assertEquals(List.of(2, 4, 6, 8), pattern.expand());
        assertEquals("2-8周(双)", pattern.toText());
    }

    @Test
    @DisplayName("单双周混排时按原始步长 1 合并，不误判")
    void mixedDoesNotMergeByTwo() {
        WeekPattern pattern = WeekPattern.fromWeeks(List.of(1, 2, 3, 4));
        assertEquals(WeekPattern.Rule.ALL, pattern.rule());
        assertEquals(1, pattern.ranges().size());
        assertEquals(List.of(1, 2, 3, 4), pattern.expand());
    }

    @Test
    @DisplayName("单周区间展开仍然只含奇数周，与合并前后等价")
    void mergedOddExpandsIdentically() {
        WeekPattern merged = WeekPattern.fromWeeks(List.of(1, 3, 5, 7, 9));
        assertEquals(List.of(1, 3, 5, 7, 9), merged.expand());
        // 与手工构造的区间展开保持一致
        WeekPattern explicit = new WeekPattern(List.of(new int[]{1, 9}), WeekPattern.Rule.ODD);
        assertEquals(explicit.expand(), merged.expand());
    }

    @Test
    @DisplayName("全偶数识别为双周")
    void evenRule() {
        WeekPattern pattern = WeekPattern.fromWeeks(List.of(2, 4, 6, 8));
        assertEquals(WeekPattern.Rule.EVEN, pattern.rule());
        assertEquals(List.of(2, 4, 6, 8), pattern.expand());
    }

    @Test
    @DisplayName("单周区间展开不会混入偶数周（C3）")
    void oddRangeDoesNotLeakEven() {
        WeekPattern pattern = new WeekPattern(List.of(new int[]{1, 16}), WeekPattern.Rule.ODD);
        List<Integer> weeks = pattern.expand();
        assertEquals(List.of(1, 3, 5, 7, 9, 11, 13, 15), weeks);
        assertTrue(weeks.stream().allMatch(w -> w % 2 == 1));
    }

    @Test
    @DisplayName("区间会被合并与排序，重叠不产生重复")
    void normalizeMerges() {
        WeekPattern pattern = new WeekPattern(
                List.of(new int[]{5, 8}, new int[]{1, 3}, new int[]{2, 5}),
                WeekPattern.Rule.ALL);
        assertEquals(1, pattern.ranges().size(), "1-8 连成一段");
        assertEquals(1, pattern.ranges().get(0)[0]);
        assertEquals(8, pattern.ranges().get(0)[1]);
    }

    @Test
    @DisplayName("越界周次被丢弃")
    void outOfRangeDropped() {
        WeekPattern pattern = new WeekPattern(
                List.of(new int[]{0, 3}, new int[]{28, 40}),
                WeekPattern.Rule.ALL);
        assertTrue(pattern.expand().stream().allMatch(w -> w >= 1 && w <= 30));
    }

    @Test
    @DisplayName("空周次：首末周为 null，展开为空")
    void emptyPattern() {
        WeekPattern pattern = WeekPattern.empty();
        assertNull(pattern.firstWeek());
        assertNull(pattern.lastWeek());
        assertTrue(pattern.expand().isEmpty());
        assertEquals(WeekPattern.Rule.ALL, pattern.rule());
    }

    @Test
    @DisplayName("toText 还原为可读文本")
    void toText() {
        WeekPattern pattern = WeekPattern.fromWeeks(
                List.of(1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16));
        assertEquals("1-3,5-16周", pattern.toText());
    }

    @Test
    @DisplayName("CourseDto.of 派生字段与真相源一致")
    void courseDtoDerivesConsistently() {
        WeekPattern pattern = new WeekPattern(List.of(new int[]{1, 16}), WeekPattern.Rule.ODD);
        CourseDto dto = CourseDto.of("数据结构", 1, 1, 2, "A101", "王五", "1-16节(单)", pattern);

        assertEquals(1, dto.startWeek());
        assertEquals(15, dto.endWeek(), "末个上课周应为 15，而不是 16");
        assertEquals(List.of(1, 3, 5, 7, 9, 11, 13, 15), dto.weeks());
        assertEquals(WeekPattern.Rule.ODD, dto.weekRule());
        assertEquals(1, dto.weekRanges().get(0)[0]);
        assertEquals(16, dto.weekRanges().get(0)[1]);
    }

    @Test
    @DisplayName("旧数据只有 weeks 时，weekPattern 能反推")
    void courseDtoBackfillsFromWeeks() {
        CourseDto legacy = new CourseDto(
                "旧课程", 1, 6, 2, 3, 4, "B202", "李四",
                List.of(1, 2, 3, 5, 6), "old", null, null);
        WeekPattern pattern = legacy.weekPattern();
        assertEquals(2, pattern.ranges().size());
        assertEquals(List.of(1, 2, 3, 5, 6), pattern.expand());
    }
}
