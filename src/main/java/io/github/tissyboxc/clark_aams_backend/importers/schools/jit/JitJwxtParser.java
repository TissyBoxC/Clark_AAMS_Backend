package io.github.tissyboxc.clark_aams_backend.importers.schools.jit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JitJwxtParser {
    private static final Map<String, Integer> WEEKDAY_MAP = new LinkedHashMap<>();
    private static final Pattern RANGE_PATTERN = Pattern.compile("(\\d+)\\s*[-~至]\\s*(\\d+)");
    private static final Pattern SECTION_RANGE_PATTERN = Pattern.compile("(\\d+)\\s*[-~至]\\s*(\\d+)\\s*节");
    private static final Pattern SECTION_SINGLE_PATTERN = Pattern.compile("(\\d+)\\s*节");
    private static final Pattern WEEK_MATCH_PATTERN = Pattern.compile("[（(](.*?周.*?)[）)]");

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
        WEEKDAY_MAP.put("一", 1);
        WEEKDAY_MAP.put("二", 2);
        WEEKDAY_MAP.put("三", 3);
        WEEKDAY_MAP.put("四", 4);
        WEEKDAY_MAP.put("五", 5);
        WEEKDAY_MAP.put("六", 6);
        WEEKDAY_MAP.put("日", 7);
        WEEKDAY_MAP.put("天", 7);
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
        return 0;
    }

    public static List<Integer> parseWeeks(String weekText) {
        List<Integer> weeks = new ArrayList<>();
        if (weekText == null || weekText.trim().isEmpty()) {
            return weeks;
        }

        String rawText = weekText.trim();
        String text = rawText
                .replace('（', ' ')
                .replace('）', ' ')
                .replace("(", "")
                .replace(")", "")
                .replace("第", "")
                .replace("周", "")
                .replace(" ", "");

        boolean odd = rawText.contains("单");
        boolean even = rawText.contains("双");

        Matcher rangeMatcher = RANGE_PATTERN.matcher(text);
        if (rangeMatcher.find()) {
            int startWeek = Integer.parseInt(rangeMatcher.group(1));
            int endWeek = Integer.parseInt(rangeMatcher.group(2));
            for (int week = startWeek; week <= endWeek; week++) {
                if (odd && week % 2 == 0) {
                    continue;
                }
                if (even && week % 2 != 0) {
                    continue;
                }
                weeks.add(week);
            }
            return weeks;
        }

        Matcher singleMatcher = Pattern.compile("(\\d+)").matcher(text);
        if (singleMatcher.find()) {
            weeks.add(Integer.parseInt(singleMatcher.group(1)));
        }

        return weeks;
    }

    public static TimeInfo parseTimeText(String timeText) {
        Integer startSection = null;
        Integer endSection = null;
        List<Integer> weeks = new ArrayList<>();

        if (timeText == null || timeText.trim().isEmpty()) {
            return new TimeInfo(startSection, endSection, weeks);
        }

        String text = timeText.trim();
        Matcher sectionRangeMatcher = SECTION_RANGE_PATTERN.matcher(text);
        if (sectionRangeMatcher.find()) {
            startSection = Integer.parseInt(sectionRangeMatcher.group(1));
            endSection = Integer.parseInt(sectionRangeMatcher.group(2));
        } else {
            Matcher singleMatcher = SECTION_SINGLE_PATTERN.matcher(text);
            if (singleMatcher.find()) {
                int section = Integer.parseInt(singleMatcher.group(1));
                startSection = section;
                endSection = section;
            }
        }

        Matcher weekMatcher = WEEK_MATCH_PATTERN.matcher(text);
        if (weekMatcher.find()) {
            weeks = parseWeeks(weekMatcher.group(1));
        }

        return new TimeInfo(startSection, endSection, weeks);
    }

    public static List<JitRawCourse> parseIndexChartsTable(String html) {
        Element table = selectTable(html);
        if (table == null) {
            throw new IllegalArgumentException("未找到课表 table#Table6");
        }

        Element tbody = table.selectFirst("tbody");
        if (tbody == null) {
            throw new IllegalArgumentException("未找到 table#Table6 的 tbody");
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
            Element timeSpan = block.selectFirst("span.time");
            Element nameSpan = block.selectFirst("span.kejie");
            Element positionSpan = block.selectFirst("span.didian");
            Element teacherSpan = firstNonNull(
                    block.selectFirst("span.teacher"),
                    block.selectFirst("span.jiaoshi"),
                    block.selectFirst("span.js"),
                    block.selectFirst("span.teach")
            );

            String timeText = textOf(timeSpan);
            String courseName = textOf(nameSpan);
            String position = textOf(positionSpan);
            String teacher = textOf(teacherSpan);

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
        Element table = document.selectFirst("body#index-charts form#form1 div.row.clsntc div#divQtxx div#divkbxs div.com-sec div.comcon-sec div.class-table table#Table6");
        if (table == null) {
            table = document.selectFirst("table#Table6");
        }
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
