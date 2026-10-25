package io.github.tissyboxc.clark_aams_backend.course;

import io.github.tissyboxc.clark_aams_backend.importers.dto.CourseDto;
import io.github.tissyboxc.clark_aams_backend.importers.dto.WeekPattern;

import java.util.ArrayList;
import java.util.List;

/**
 * 课表写接口的请求体（P3-1）。
 *
 * <p>不复用 {@link CourseDto} 作请求体，原因有二：
 * <ul>
 *   <li>{@code CourseDto.weekRanges} 是 {@code List<int[]>}，
 *       作为请求体反序列化时不稳（Jackson 对裸数组类型的绑定需要额外配置），
 *       实测会直接 400 且不留日志</li>
 *   <li>请求只需要「真相源」字段，派生字段（weekRanges/weeks/startWeek/endWeek）
 *       让客户端提供反而容易自相矛盾</li>
 * </ul>
 *
 * <p>因此请求体只接受最少信息：{@code weeks}（离散周次）或
 * {@code weekRanges + weekRule}（紧凑形态），二者给其一即可。
 */
public record CourseUpsertRequest(
        String name,
        Integer dayOfWeek,
        Integer startLesson,
        Integer endLesson,
        String location,
        String teacher,
        String rawTime,
        List<Integer> weeks,
        List<List<Integer>> weekRanges,
        String weekRule
) {

    /** 转成内部使用的 {@link CourseDto}（派生字段在此统一计算）。 */
    public CourseDto toCourseDto() {
        WeekPattern pattern = resolvePattern();
        return CourseDto.of(
                name,
                dayOfWeek,
                startLesson,
                endLesson,
                location == null ? "" : location,
                teacher == null ? "" : teacher,
                rawTime == null ? "" : rawTime,
                pattern
        );
    }

    /** 优先用紧凑形态 weekRanges，其次用离散 weeks。 */
    public WeekPattern resolvePattern() {
        List<int[]> ranges = new ArrayList<>();

        if (weekRanges != null && !weekRanges.isEmpty()) {
            for (List<Integer> pair : weekRanges) {
                if (pair == null || pair.size() < 2) {
                    continue;
                }
                Integer start = pair.get(0);
                Integer end = pair.get(1);
                if (start == null || end == null) {
                    continue;
                }
                ranges.add(new int[]{start, end});
            }
            return new WeekPattern(ranges, parseRule(weekRule));
        }

        if (weeks != null && !weeks.isEmpty()) {
            return WeekPattern.fromWeeks(weeks);
        }

        return WeekPattern.empty();
    }

    private static WeekPattern.Rule parseRule(String wire) {
        if (wire == null) {
            return WeekPattern.Rule.ALL;
        }
        try {
            return WeekPattern.Rule.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return WeekPattern.Rule.ALL;
        }
    }
}
