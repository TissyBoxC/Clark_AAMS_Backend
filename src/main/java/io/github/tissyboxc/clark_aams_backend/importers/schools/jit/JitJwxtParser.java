package io.github.tissyboxc.clark_aams_backend.importers.schools.jit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JitJwxtParser {
    private static final Map<String, Integer> WEEKDAY_MAP = new LinkedHashMap<>();
    private static final Pattern RANGE_PATTERN = Pattern.compile("(\\d+)\\s*[-~]\\s*(\\d+)");
    private static final Pattern SINGLE_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern BRACKET_WEEK_PATTERN = Pattern.compile("[\\(（]([^\\)）]*周[^\\)）]*)[\\)）]");
    /** 节次区间：兼容「1-2节」「第1-2节」「第7节-第8节」「第7-8节」。 */
    private static final Pattern SECTION_RANGE_PATTERN =
            Pattern.compile("第?\\s*(\\d+)\\s*节?\\s*[-~]\\s*第?\\s*(\\d+)\\s*节");
    /** 单节次：必须带「节」字，否则 "1-16周" 里的数字会被当成节次。 */
    private static final Pattern SECTION_SINGLE_PATTERN =
            Pattern.compile("第?\\s*(\\d+)\\s*节");
    /** 周次片段分隔符：半角/全角逗号、顿号、分号。 */
    private static final Pattern WEEK_SPLIT_PATTERN = Pattern.compile("[,，、;；]+");
    /** 单周/双周标记。 */
    private static final Pattern ODD_MARKER = Pattern.compile("单周?|【单】|\\[单\\]");
    private static final Pattern EVEN_MARKER = Pattern.compile("双周?|【双】|\\[双\\]");

    static {
        WEEKDAY_MAP.put("星期一", 1);
        WEEKDAY_MAP.put("星期二", 2);
        WEEKDAY_MAP.put("星期三", 3);
        WEEKDAY_MAP.put("星期四", 4);
        WEEKDAY_MAP.put("星期五", 5);
        WEEKDAY_MAP.put("星期六", 6);
        WEEKDAY_MAP.put("星期日", 7);
        WEEKDAY_MAP.put("星期天", 7);
        WEEKDAY_MAP.put("周一", 1);
        WEEKDAY_MAP.put("周二", 2);
        WEEKDAY_MAP.put("周三", 3);
        WEEKDAY_MAP.put("周四", 4);
        WEEKDAY_MAP.put("周五", 5);
        WEEKDAY_MAP.put("周六", 6);
        WEEKDAY_MAP.put("周日", 7);
        WEEKDAY_MAP.put("周天", 7);
    }

    private JitJwxtParser() {
    }

    public static boolean pageHasTable6(String html) {
        return selectTable(html) != null;
    }

    public static int parseDay(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            return 0;
        }
        for (Map.Entry<String, Integer> entry : WEEKDAY_MAP.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        Matcher numericMatcher = SINGLE_PATTERN.matcher(normalized);
        if (numericMatcher.find()) {
            int day = Integer.parseInt(numericMatcher.group(1));
            return day >= 1 && day <= 7 ? day : 0;
        }
        return 0;
    }

    /**
     * 解析周次文本，返回升序去重的周次列表。
     *
     * <p>修复 C1：原实现只取第一个「a-b」区间就 return，导致
     * {@code 1-3周,5-16周} 丢掉 5-16 周、{@code 3,5,7周} 直接返回空。
     * 现在按逗号/顿号切分成多个片段逐段解析，聚合后再排序去重。
     *
     * <p>单双周标记既可能出现在整体（{@code 1-16周(单)}），
     * 也可能出现在单个片段（{@code 1-8周(单),10-12周}），因此先取整体标记，
     * 解析每个片段时再与片段自身的标记求并。
     */
    public static List<Integer> parseWeeks(String weekText) {
        if (weekText == null || weekText.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String normalized = weekText
                .replace("第", "")
                .replace("周", "")
                .replace("(", " ")
                .replace(")", " ")
                .replace("（", " ")
                .replace("）", " ")
                .trim();

        // 整体单双周标记（片段自身标记会在 parseWeekFragment 里再叠加）
        boolean globalOdd = ODD_MARKER.matcher(normalized).find();
        boolean globalEven = EVEN_MARKER.matcher(normalized).find();
        boolean globalSelected = globalOdd || globalEven;

        // 从整体文本里剥掉标记，避免标记字符干扰数字解析
        String stripped = ODD_MARKER.matcher(normalized).replaceAll(" ");
        stripped = EVEN_MARKER.matcher(stripped).replaceAll(" ");

        List<Integer> weeks = new ArrayList<>();
        for (String fragment : WEEK_SPLIT_PATTERN.split(stripped)) {
            String piece = fragment.trim();
            if (piece.isEmpty()) {
                continue;
            }
            boolean odd = globalOdd;
            boolean even = globalEven;
            if (!globalSelected) {
                // 片段级标记，例如 "1-8(单)"
                odd = ODD_MARKER.matcher(piece).find();
                even = EVEN_MARKER.matcher(piece).find();
                piece = ODD_MARKER.matcher(piece).replaceAll(" ");
                piece = EVEN_MARKER.matcher(piece).replaceAll(" ");
            }
            addFragmentWeeks(piece, odd, even, weeks);
        }

        return weeks.stream().distinct().sorted().toList();
    }

    /**
     * 解析单个周次片段（已不含单双周标记），把结果追加到 {@code out}。
     *
     * <p>片段可能包含一个区间（{@code 1-8}）或多个裸数字（{@code 5}）。
     * 若两种都没有，说明这一段不是周次（例如课程名残留），直接忽略。
     */
    private static void addFragmentWeeks(String piece, boolean odd, boolean even, List<Integer> out) {
        List<int[]> ranges = new ArrayList<>();

        Matcher rangeMatcher = RANGE_PATTERN.matcher(piece);
        while (rangeMatcher.find()) {
            int start = Integer.parseInt(rangeMatcher.group(1));
            int end = Integer.parseInt(rangeMatcher.group(2));
            // 业务校验：周次落在 1..30 且 end >= start
            if (start >= 1 && end <= 30 && end >= start) {
                ranges.add(new int[]{start, end});
            }
        }

        if (ranges.isEmpty()) {
            Matcher singleMatcher = SINGLE_PATTERN.matcher(piece);
            while (singleMatcher.find()) {
                int week = Integer.parseInt(singleMatcher.group(1));
                if (week >= 1 && week <= 30) {
                    ranges.add(new int[]{week, week});
                }
            }
        }

        for (int[] range : ranges) {
            for (int week = range[0]; week <= range[1]; week++) {
                if (odd && week % 2 == 0) {
                    continue;
                }
                if (even && week % 2 != 0) {
                    continue;
                }
                out.add(week);
            }
        }
    }

    /**
     * 从时间文本里同时解析节次与周次。
     *
     * <p>修复 C2：原实现用 {@code (\d+)\s*节?} 匹配单节次，"节"字可选导致
     * {@code 第1-3周,星期1,第7节-第8节} 会先吃到周次里的 {@code 1}。
     * 现在先把周次片段从文本中剥离，再在剩余文本里按「必须带节字」的模式解析节次；
     * 若剥离后找不到节次，再回退到原文（兼容只在文本里写节次、周次另给的页面）。
     */
    public static TimeInfo parseTimeText(String timeText) {
        Integer startSection = null;
        Integer endSection = null;
        List<Integer> weeks = new ArrayList<>();

        if (timeText == null || timeText.trim().isEmpty()) {
            return new TimeInfo(startSection, endSection, weeks);
        }

        String normalized = timeText.trim();

        // 先在原文里取周次（括号内或全文），供后面剥离使用
        String weekSegment = null;
        Matcher weekMatcher = BRACKET_WEEK_PATTERN.matcher(normalized);
        if (weekMatcher.find()) {
            weekSegment = weekMatcher.group(1);
        } else if (normalized.contains("周")) {
            weekSegment = normalized;
        }
        if (weekSegment != null) {
            weeks = parseWeeks(weekSegment);
        }

        // 剥离周次片段后再解析节次，避免周次数字被当成节次
        String sectionSource = normalized;
        if (weekSegment != null && !weekSegment.isBlank()) {
            sectionSource = normalized.replace(weekSegment, " ");
        }
        // 去掉「星期N」/「周N」中的星期数字
        sectionSource = sectionSource
                .replaceAll("星期\\s*[一二三四五六日天1-7]", " ")
                .replaceAll("周\\s*[一二三四五六日天]", " ")
                .replaceAll("[\\d]+\\s*[-~]\\s*[\\d]+\\s*周", " ")
                .replaceAll("第?\\s*[\\d]+\\s*周", " ");

        SectionPair pair = matchSections(sectionSource);
        if (pair == null) {
            // 回退：有些页面把节次写在别处，剥太狠会漏掉
            pair = matchSections(normalized);
        }
        if (pair != null) {
            startSection = pair.start();
            endSection = pair.end();
        }

        return new TimeInfo(startSection, endSection, weeks);
    }

    /** 在一段文本里匹配节次，优先区间，其次单节；都找不到返回 null。 */
    private static SectionPair matchSections(String text) {
        Matcher rangeMatcher = SECTION_RANGE_PATTERN.matcher(text);
        if (rangeMatcher.find()) {
            int start = Integer.parseInt(rangeMatcher.group(1));
            int end = Integer.parseInt(rangeMatcher.group(2));
            if (start >= 1 && end <= 24 && end >= start) {
                return new SectionPair(start, end);
            }
        }
        Matcher singleMatcher = SECTION_SINGLE_PATTERN.matcher(text);
        if (singleMatcher.find()) {
            int section = Integer.parseInt(singleMatcher.group(1));
            if (section >= 1 && section <= 24) {
                return new SectionPair(section, section);
            }
        }
        return null;
    }

    private record SectionPair(int start, int end) {
    }

    public static List<JitRawCourse> parseIndexChartsTable(String html) {
        Element table = selectTable(html);
        if (table == null) {
            throw new IllegalArgumentException("table#Table6 not found");
        }

        Element tbody = table.selectFirst("tbody");
        if (tbody == null) {
            throw new IllegalArgumentException("table#Table6 tbody not found");
        }

        List<JitRawCourse> courses = new ArrayList<>();
        for (Element tr : tbody.children()) {
            if (!"tr".equals(tr.tagName())) {
                continue;
            }

            List<Element> tds = tr.children().stream()
                    .filter(element -> "td".equals(element.tagName()))
                    .toList();
            if (tds.isEmpty()) {
                continue;
            }

            int day = parseDay(tds.get(0).text());
            if (day <= 0) {
                continue;
            }

            for (int index = 1; index < tds.size(); index++) {
                courses.addAll(parseCourseTd(tds.get(index), day));
            }
        }

        List<JitRawCourse> uniqueCourses = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (JitRawCourse course : courses) {
            String key = String.join("|",
                    Objects.toString(course.name(), ""),
                    Integer.toString(course.day()),
                    Objects.toString(course.startSection(), ""),
                    Objects.toString(course.endSection(), ""),
                    Objects.toString(course.position(), ""),
                    course.weeks().toString());
            if (seen.contains(key)) {
                continue;
            }
            seen.add(key);
            uniqueCourses.add(course);
        }

        return uniqueCourses;
    }

    private static List<JitRawCourse> parseCourseTd(Element td, int day) {
        List<JitRawCourse> courses = new ArrayList<>();
        List<Element> blocks = td.select("p.kblsbk");
        if (blocks.isEmpty()) {
            return courses;
        }

        for (Element block : blocks) {
            String timeText = textOf(block.selectFirst("span.time"));
            String courseName = textOf(block.selectFirst("span.kejie"));
            String position = textOf(block.selectFirst("span.didian"));
            String teacher = textOf(firstNonNull(
                    block.selectFirst("span.teacher"),
                    block.selectFirst("span.jiaoshi"),
                    block.selectFirst("span.js"),
                    block.selectFirst("span.teach")
            ));

            if (courseName.isBlank()) {
                continue;
            }

            TimeInfo timeInfo = parseTimeText(timeText);
            courses.add(new JitRawCourse(
                    courseName,
                    teacher,
                    position,
                    day,
                    timeInfo.startSection(),
                    timeInfo.endSection(),
                    timeInfo.weeks(),
                    timeText
            ));
        }

        return courses;
    }

    private static Element selectTable(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Document document = Jsoup.parse(html);
        Element table = document.selectFirst("table#Table6");
        if (table == null) {
            table = document.selectFirst(".class-table table");
        }
        return table;
    }

    private static String textOf(Element element) {
        return element == null ? "" : element.text().trim();
    }

    @SafeVarargs
    private static Element firstNonNull(Element... elements) {
        for (Element element : elements) {
            if (element != null) {
                return element;
            }
        }
        return null;
    }

    public record TimeInfo(Integer startSection, Integer endSection, List<Integer> weeks) {
    }

    public record JitRawCourse(
            String name,
            String teacher,
            String position,
            int day,
            Integer startSection,
            Integer endSection,
            List<Integer> weeks,
            String rawTime
    ) {
    }
}
