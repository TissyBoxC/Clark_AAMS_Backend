package io.github.tissyboxc.clark_aams_backend.importers.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 周次规则：用「若干连续区间 + 单双周标记」描述上课周次。
 *
 * <p>这是 P0-1 引入的唯一真相源。此前的 {@code CourseDto} 同时暴露
 * {@code weeks}（展开数组）与 {@code startWeek/endWeek}（首末项），
 * 二者可能互相矛盾：
 * <ul>
 *   <li>{@code 1-3,5-16} 会被压平成 {@code 1..16}，凭空多出第 4 周；</li>
 *   <li>{@code 1-16(单)} 会被压平成 {@code 1..15}，把偶数周也算进来。</li>
 * </ul>
 *
 * <p>建模为「区间 + 奇偶」后，语义无损，展开成离散周次只是派生操作。
 * 这与主流课程表实现（WakeUp 课程的 {@code startWeek/endWeek/weekType}、
 * Class-Schedule 的 {@code WeekRule}）一致。
 *
 * @param ranges 非空的连续区间列表，每项形如 {@code [start, end]}，均落在 1..30 且 start<=end
 * @param rule   单双周标记：ALL / ODD / EVEN
 */
public record WeekPattern(List<int[]> ranges, Rule rule) {

    /** 单双周标记。 */
    public enum Rule {
        /** 每周上课。 */
        ALL,
        /** 仅单周上课。 */
        ODD,
        /** 仅双周上课。 */
        EVEN
    }

    /** 防御性：统一构造入口，保证 ranges 有序、去重叠、合法。 */
    public WeekPattern {
        List<int[]> normalized = normalize(ranges);
        ranges = List.copyOf(normalized);
        if (rule == null) {
            rule = Rule.ALL;
        }
    }

    /** 空周次。 */
    public static WeekPattern empty() {
        return new WeekPattern(List.of(), Rule.ALL);
    }

    /**
     * 从离散周次列表反推周次规则。
     *
     * <p>用于兼容只给出 {@code weeks} 的数据来源。会先判断是否为纯单/双周，
     * 再按连续段合并成区间。
     */
    public static WeekPattern fromWeeks(List<Integer> weeks) {
        if (weeks == null || weeks.isEmpty()) {
            return empty();
        }
        List<Integer> sorted = weeks.stream()
                .filter(w -> w != null && w >= 1 && w <= 30)
                .distinct()
                .sorted()
                .toList();
        if (sorted.isEmpty()) {
            return empty();
        }

        // 判定单双周：全部奇数 => ODD，全部偶数 => EVEN，否则 ALL
        boolean allOdd = sorted.stream().allMatch(w -> w % 2 == 1);
        boolean allEven = sorted.stream().allMatch(w -> w % 2 == 0);
        Rule rule = Rule.ALL;
        List<Integer> forRanges = sorted;
        if (allOdd && sorted.size() > 1) {
            rule = Rule.ODD;
        } else if (allEven && sorted.size() > 1) {
            rule = Rule.EVEN;
        }

        List<int[]> ranges = new ArrayList<>();
        int start = forRanges.get(0);
        int prev = start;
        for (int i = 1; i < forRanges.size(); i++) {
            int current = forRanges.get(i);
            if (current == prev + 1) {
                prev = current;
                continue;
            }
            ranges.add(new int[]{start, prev});
            start = current;
            prev = current;
        }
        ranges.add(new int[]{start, prev});
        return new WeekPattern(ranges, rule);
    }

    /** 展开为升序离散周次。 */
    public List<Integer> expand() {
        Set<Integer> out = new LinkedHashSet<>();
        for (int[] range : ranges) {
            for (int week = range[0]; week <= range[1]; week++) {
                if (rule == Rule.ODD && week % 2 == 0) {
                    continue;
                }
                if (rule == Rule.EVEN && week % 2 != 0) {
                    continue;
                }
                out.add(week);
            }
        }
        return out.stream().sorted().toList();
    }

    /** 首个上课周，无周次时为 null。 */
    public Integer firstWeek() {
        List<Integer> all = expand();
        return all.isEmpty() ? null : all.get(0);
    }

    /** 末个上课周，无周次时为 null。 */
    public Integer lastWeek() {
        List<Integer> all = expand();
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    /** 用于展示的紧凑文本，例如 {@code 1-3,5-16周(单)}。 */
    public String toText() {
        if (ranges.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int[] range : ranges) {
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            if (range[0] == range[1]) {
                builder.append(range[0]);
            } else {
                builder.append(range[0]).append('-').append(range[1]);
            }
        }
        builder.append('周');
        if (rule == Rule.ODD) {
            builder.append("(单)");
        } else if (rule == Rule.EVEN) {
            builder.append("(双)");
        }
        return builder.toString();
    }

    /** 排序 + 合并重叠区间，丢弃非法项。 */
    private static List<int[]> normalize(List<int[]> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            return List.of();
        }
        List<int[]> valid = ranges.stream()
                .filter(r -> r != null && r.length >= 2)
                .map(r -> new int[]{Math.min(r[0], r[1]), Math.max(r[0], r[1])})
                .filter(r -> r[0] >= 1 && r[1] <= 30)
                .sorted((a, b) -> Integer.compare(a[0], b[0]))
                .toList();
        if (valid.isEmpty()) {
            return List.of();
        }
        List<int[]> merged = new ArrayList<>();
        int[] current = valid.get(0).clone();
        for (int i = 1; i < valid.size(); i++) {
            int[] next = valid.get(i);
            if (next[0] <= current[1] + 1) {
                current[1] = Math.max(current[1], next[1]);
            } else {
                merged.add(current);
                current = next.clone();
            }
        }
        merged.add(current);
        return merged;
    }
}
