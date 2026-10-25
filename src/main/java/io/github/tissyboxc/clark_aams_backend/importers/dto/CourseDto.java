package io.github.tissyboxc.clark_aams_backend.importers.dto;

import java.util.List;

/**
 * 课程数据传输对象。
 *
 * <p>P0-1 之后，周次的**唯一真相源**是 {@link #weekRanges()} + {@link #weekRule()}。
 * {@link #weeks()} 与 {@link #startWeek()}/{@link #endWeek()} 是派生字段，仅为兼容
 * 尚未升级的客户端而保留，由 {@link #of} 统一计算，**不要手工填写**。
 *
 * @param name         课程名
 * @param startWeek    派生：首个上课周（可能为 null）
 * @param endWeek      派生：末个上课周（可能为 null）
 * @param dayOfWeek    星期一~星期日 = 1~7
 * @param startLesson  开始节次（可能为 null，表示页面未给出）
 * @param endLesson    结束节次（可能为 null）
 * @param location     上课地点
 * @param teacher      教师
 * @param weeks        派生：展开后的离散周次，升序
 * @param rawTime      原始时间文本，便于排错
 * @param weekRanges   真相源：连续周次区间，每项 {@code [start, end]}
 * @param weekRule     真相源：单双周标记 ALL/ODD/EVEN
 */
public record CourseDto(
        String name,
        Integer startWeek,
        Integer endWeek,
        Integer dayOfWeek,
        Integer startLesson,
        Integer endLesson,
        String location,
        String teacher,
        List<Integer> weeks,
        String rawTime,
        List<int[]> weekRanges,
        WeekPattern.Rule weekRule
) {

    /**
     * 唯一构造入口：由 {@link WeekPattern} 派生所有冗余字段。
     *
     * <p>这样 {@code weeks} / {@code startWeek} / {@code endWeek} 不可能与
     * {@code weekRanges} 不一致，从根上消除双真相源。
     */
    public static CourseDto of(
            String name,
            Integer dayOfWeek,
            Integer startLesson,
            Integer endLesson,
            String location,
            String teacher,
            String rawTime,
            WeekPattern pattern
    ) {
        WeekPattern safe = pattern == null ? WeekPattern.empty() : pattern;
        return new CourseDto(
                name,
                safe.firstWeek(),
                safe.lastWeek(),
                dayOfWeek,
                startLesson,
                endLesson,
                location,
                teacher,
                safe.expand(),
                rawTime,
                safe.ranges(),
                safe.rule()
        );
    }

    /** 取出周次规则（真相源）。旧数据没有 weekRanges 时，从 weeks 反推。 */
    public WeekPattern weekPattern() {
        if (weekRanges != null && !weekRanges.isEmpty()) {
            return new WeekPattern(weekRanges, weekRule);
        }
        return WeekPattern.fromWeeks(weeks);
    }
}
