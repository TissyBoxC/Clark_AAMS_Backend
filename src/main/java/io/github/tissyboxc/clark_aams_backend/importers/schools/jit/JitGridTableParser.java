package io.github.tissyboxc.clark_aams_backend.importers.schools.jit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析「格子表格」形态的正方课表。
 *
 * <p>有些学校的课表页不是 {@code p.kblsbk} 块结构，而是一张
 * {@code table#Table1.schedule}：
 *
 * <pre>
 * 时间     | 星期一 | 星期二 | ...
 * 上午 r=4 |       |       |
 * 第1节    | 安卓系统开发        |
 *          | 周一第1,2节{第1-12周} |
 *          | 何云红               |
 *          | 计算机软件实验室      |
 * </pre>
 *
 * <p>每个格子里是纯文本，多行之间用 {@code <br>} 分隔，同一格有多门课时
 * 用空行（{@code <br><br>}）隔开。一行课程固定是这个顺序：
 * 课程名 / 周次节次 / 教师 / 地点。
 *
 * <p>因此不能简单按行读格子——{@code rowspan} 会让后面的行缺列，
 * 必须先把表格摊平成「行列 → 单元格」的网格，再定位每个格子所在的星期列。
 */
final class JitGridTableParser {

    /** 可选的课表容器，按优先级。 */
    private static final String[] TABLE_SELECTORS = {
            "table#Table1.schedule",
            "table#Table1",
            "table.schedule",
            "table#Table6",
    };

    /** 时间列里出现这些字说明是分段标签（早晨/上午/下午/晚上），不是节次。 */
    private static final List<String> PERIOD_LABELS = List.of("早晨", "上午", "下午", "晚上", "中午");

    /** 一行课程里的「周次节次」，如「周一第1,2节{第1-12周}」。 */
    private static final Pattern TIME_LINE = Pattern.compile(
            "周[一二三四五六日].*?第\\s*\\d+.*?节",
            Pattern.DOTALL
    );

    private JitGridTableParser() {
    }

    /** 页面上有没有这种格子课表。 */
    static boolean looksLikeGridSchedule(String html) {
        return selectTable(html) != null;
    }

    static Element selectTable(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Document document = Jsoup.parse(html);
        for (String selector : TABLE_SELECTORS) {
            Element table = document.selectFirst(selector);
            if (table != null && table.select("td").size() > 10) {
                return table;
            }
        }
        return null;
    }

    /**
     * 解析整张表，返回课程列表。解析不出任何课程时返回空列表
     * （由上层决定是换候选 HTML 还是报失败）。
     */
    static List<JitJwxtParser.JitRawCourse> parse(String html) {
        Element table = selectTable(html);
        if (table == null) {
            return List.of();
        }

        List<List<Element>> grid = flatten(table);
        if (grid.isEmpty()) {
            return List.of();
        }

        // 表头：找出「星期一…星期日」各在哪一列
        Map<Integer, Integer> dayByColumn = locateDayColumns(grid);

        List<JitJwxtParser.JitRawCourse> courses = new ArrayList<>();
        for (List<Element> row : grid) {
            int section = -1;
            for (int column = 0; column < row.size(); column++) {
                Element cell = row.get(column);

                // 时间列里的「第N节」决定了这一行的节次
                int parsed = parseSectionIndex(cell == null ? "" : cell.text());
                if (parsed > 0) {
                    section = parsed;
                }
            }

            for (int column = 0; column < row.size(); column++) {
                Integer day = dayByColumn.get(column);
                if (day == null) {
                    continue;
                }
                Element cell = row.get(column);
                if (cell == null) {
                    continue;
                }
                int cellSection = section > 0 ? section : resolveSectionFromCell(cell);
                courses.addAll(parseCell(cell, day, cellSection));
            }
        }

        return mergeAdjacent(courses);
    }

    /**
     * 把「同一门课连着上」的多条记录合成一条。
     *
     * <p>教务系统常把一门连堂课按两节一格拆开，比如大学物理C 在周日：
     * 第1,2节、第3,4节、第5,6节、第7,8节，四个独立单元格。
     * 原样传下去，课表上就是四个色块、四种颜色——可它其实是同一门课，
     * 连着上八节。
     *
     * <p>合并条件全部满足才算连堂：
     * <ul>
     *   <li>同一天</li>
     *   <li>课名、教师、地点相同</li>
     *   <li>周次集合完全相同（周次不同的不是同一段课）</li>
     *   <li>节次首尾相接或重叠：{@code 下一段的开始 <= 上一段的结束 + 1}</li>
     * </ul>
     *
     * <p>1234、5678 这种也算连续——只要中间没有空档就一直往下并。
     */
    static List<JitJwxtParser.JitRawCourse> mergeAdjacent(List<JitJwxtParser.JitRawCourse> courses) {
        if (courses == null || courses.size() < 2) {
            return courses == null ? List.of() : courses;
        }

        // 先按 天 → 起始节 排序，让连堂课相邻
        List<JitJwxtParser.JitRawCourse> sorted = new ArrayList<>(courses);
        sorted.sort((a, b) -> {
            int byDay = Integer.compare(a.day(), b.day());
            if (byDay != 0) {
                return byDay;
            }
            return Integer.compare(sectionOrZero(a.startSection()), sectionOrZero(b.startSection()));
        });

        List<JitJwxtParser.JitRawCourse> merged = new ArrayList<>();
        for (JitJwxtParser.JitRawCourse course : sorted) {
            if (merged.isEmpty()) {
                merged.add(course);
                continue;
            }

            JitJwxtParser.JitRawCourse last = merged.get(merged.size() - 1);
            if (canJoin(last, course)) {
                merged.set(merged.size() - 1, concatenate(last, course));
            } else {
                merged.add(course);
            }
        }

        return merged;
    }

    /** 两条记录能不能并成一条连堂课。 */
    private static boolean canJoin(JitJwxtParser.JitRawCourse a, JitJwxtParser.JitRawCourse b) {
        if (a.day() != b.day()) {
            return false;
        }
        if (!equalText(a.name(), b.name()) || !equalText(a.teacher(), b.teacher())
                || !equalText(a.position(), b.position())) {
            return false;
        }
        if (!a.weeks().equals(b.weeks())) {
            return false;
        }
        int endOfA = a.endSection() != null ? a.endSection() : sectionOrZero(a.startSection());
        int startOfB = sectionOrZero(b.startSection());
        // 首尾相接（end+1 == start）或重叠（end >= start）
        return startOfB > 0 && endOfA > 0 && startOfB <= endOfA + 1;
    }

    /** 合并两条：起止取并集，原始时间文本拼起来便于回溯。 */
    private static JitJwxtParser.JitRawCourse concatenate(
            JitJwxtParser.JitRawCourse a,
            JitJwxtParser.JitRawCourse b) {
        int start = Math.min(sectionOrZero(a.startSection()), sectionOrZero(b.startSection()));
        int end = Math.max(
                a.endSection() != null ? a.endSection() : sectionOrZero(a.startSection()),
                b.endSection() != null ? b.endSection() : sectionOrZero(b.startSection()));

        String rawTime = a.rawTime();
        if (b.rawTime() != null && !b.rawTime().equals(rawTime)) {
            rawTime = (rawTime == null || rawTime.isBlank()) ? b.rawTime() : rawTime + " / " + b.rawTime();
        }

        return new JitJwxtParser.JitRawCourse(
                a.name(),
                a.teacher(),
                a.position(),
                a.day(),
                start > 0 ? start : null,
                end > 0 ? end : null,
                a.weeks(),
                rawTime
        );
    }

    private static boolean equalText(String a, String b) {
        String left = a == null ? "" : a.trim();
        String right = b == null ? "" : b.trim();
        return left.equals(right);
    }

    private static int sectionOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 把带 rowspan/colspan 的表格摊平成规整网格。
     *
     * <p>被 rowspan 占住的格子会在后续行里补上同一个 Element，
     * 这样按「列号」找星期才是对的。
     */
    private static List<List<Element>> flatten(Element table) {
        Elements rows = table.select("tr");
        List<List<Element>> grid = new ArrayList<>();
        // 记录每列被上方 rowspan 占用的剩余行数，以及占用的那个单元格
        Map<Integer, Integer> spanLeft = new LinkedHashMap<>();
        Map<Integer, Element> spanCell = new LinkedHashMap<>();

        for (Element row : rows) {
            List<Element> current = new ArrayList<>();

            // 先按列号填充来自上方 rowspan 的格子
            int column = 0;
            while (spanLeft.getOrDefault(column, 0) > 0) {
                while (current.size() < column) {
                    current.add(null);
                }
                current.add(spanCell.get(column));
                spanLeft.put(column, spanLeft.get(column) - 1);
                column++;
            }

            for (Element cell : row.children()) {
                if (!cell.tagName().equals("td") && !cell.tagName().equals("th")) {
                    continue;
                }
                // 跳过已经被上方 rowspan 占住的列
                while (current.size() < column) {
                    current.add(null);
                }

                int colspan = intAttr(cell, "colspan", 1);
                int rowspan = intAttr(cell, "rowspan", 1);

                for (int i = 0; i < colspan; i++) {
                    current.add(cell);
                    if (rowspan > 1) {
                        int target = column + i;
                        spanLeft.put(target, rowspan - 1);
                        spanCell.put(target, cell);
                    }
                }
                column += colspan;
            }
            grid.add(current);
        }
        return grid;
    }

    /** 找出表头里「星期一…星期日」所在的列号（从 1 开始）。 */
    private static Map<Integer, Integer> locateDayColumns(List<List<Element>> grid) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (List<Element> row : grid) {
            boolean found = false;
            for (int column = 0; column < row.size(); column++) {
                Element cell = row.get(column);
                if (cell == null) {
                    continue;
                }
                int day = JitJwxtParser.parseDay(cell.text());
                if (day > 0) {
                    result.putIfAbsent(column, day);
                    found = true;
                }
            }
            if (found && result.size() >= 5) {
                break;
            }
        }
        return result;
    }

    /** 从「第3节」这类文字里取出节次数。 */
    private static int parseSectionIndex(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }
        String trimmed = text.trim();
        for (String label : PERIOD_LABELS) {
            if (trimmed.equals(label)) {
                return -1; // 只是分段标签
            }
        }
        Matcher matcher = Pattern.compile("第\\s*(\\d+)\\s*节").matcher(trimmed);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return -1;
    }

    /** 格子自己带了「周X第N节」时，用它兜底。 */
    private static int resolveSectionFromCell(Element cell) {
        String text = cell.text();
        Matcher matcher = Pattern.compile("第\\s*(\\d+)\\s*[-,]").matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return -1;
    }

    /**
     * 解析一个格子里的一门或多门课。
     *
     * <p>格子用 {@code <br>} 分行；空行（连续两个 br）分隔不同课程，
     * 每门课固定四行：课程名 / 周次节次 / 教师 / 地点。
     */
    private static List<JitJwxtParser.JitRawCourse> parseCell(Element cell, int day, int fallbackSection) {
        List<JitJwxtParser.JitRawCourse> courses = new ArrayList<>();

        List<String> lines = splitLines(cell);
        if (lines.isEmpty()) {
            return courses;
        }

        List<List<String>> groups = groupByBlankLine(lines);
        for (List<String> group : groups) {
            JitJwxtParser.JitRawCourse course = buildCourse(group, day, fallbackSection);
            if (course != null) {
                courses.add(course);
            }
        }
        return courses;
    }

    /**
     * 把单元格按 {@code <br>} 拆成若干行文本。
     *
     * <p>不能用 {@code cell.html()} 再拿正则处理——那样拿到的
     * {@code <br>} 是好着的，但文字里的 {@code <} 也被转义了，
     * 反而分不清哪个是标签。这里直接走子节点，遇到 br 就切一行。
     */
    private static List<String> splitLines(Element cell) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (org.jsoup.nodes.Node node : cell.childNodes()) {
            if (node instanceof org.jsoup.nodes.Element element && element.tagName().equals("br")) {
                lines.add(current.toString().trim());
                current.setLength(0);
            } else if (node instanceof org.jsoup.nodes.TextNode textNode) {
                current.append(textNode.text());
            } else if (node instanceof org.jsoup.nodes.Element inner) {
                // 有些模板把内容包在 <span>/<p> 里
                current.append(inner.text());
            }
        }
        lines.add(current.toString().trim());

        return lines;
    }

    /** 用空行把行分组，每组是一门课。 */
    private static List<List<String>> groupByBlankLine(List<String> lines) {
        List<List<String>> groups = new ArrayList<>();
        List<String> current = new ArrayList<>();

        for (String line : lines) {
            if (line.isEmpty()) {
                if (!current.isEmpty()) {
                    groups.add(current);
                    current = new ArrayList<>();
                }
            } else {
                current.add(line);
            }
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        return groups;
    }

    /** 按「课程名 / 周次节次 / 教师 / 地点」的固定顺序拼出一门课。 */
    private static JitJwxtParser.JitRawCourse buildCourse(List<String> group, int day, int fallbackSection) {
        if (group.isEmpty()) {
            return null;
        }

        String name = group.get(0);
        if (name.isBlank() || name.equals("&nbsp;") || name.equals(" ")) {
            return null;
        }

        // 表头「星期一…星期日」、分段标签「上午/下午」会落进课程位，跳过
        for (String label : PERIOD_LABELS) {
            if (name.equals(label)) {
                return null;
            }
        }
        if (JitJwxtParser.parseDay(name) > 0) {
            return null;
        }

        // 找到「周X第N节」那一行，它之前是课名，之后依次是教师、地点
        int timeIndex = -1;
        String timeLine = "";
        for (int i = 0; i < group.size(); i++) {
            if (TIME_LINE.matcher(group.get(i)).find()) {
                timeIndex = i;
                timeLine = group.get(i);
                break;
            }
        }

        String teacher = "";
        String position = "";

        if (timeIndex >= 0) {
            // 课名可能跨了 timeIndex 之前的若干行
            name = String.join("", group.subList(0, timeIndex)).trim();
            if (timeIndex + 1 < group.size()) {
                teacher = group.get(timeIndex + 1);
            }
            if (timeIndex + 2 < group.size()) {
                position = group.get(timeIndex + 2);
            }
        }

        if (name.isBlank()) {
            return null;
        }

        JitJwxtParser.TimeInfo timeInfo = timeLine.isBlank()
                ? new JitJwxtParser.TimeInfo(fallbackSection > 0 ? fallbackSection : null,
                fallbackSection > 0 ? fallbackSection : null, List.of())
                : JitJwxtParser.parseTimeText(normalizeTimeText(timeLine));

        int courseDay = day;
        if (timeLine.contains("周")) {
            int parsedDay = JitJwxtParser.parseDay(timeLine);
            if (parsedDay > 0) {
                courseDay = parsedDay;
            }
        }

        return new JitJwxtParser.JitRawCourse(
                name,
                teacher,
                position,
                courseDay,
                timeInfo.startSection(),
                timeInfo.endSection(),
                timeInfo.weeks(),
                timeLine
        );
    }

    /**
     * 把格子里的时间文本改写成 {@link JitJwxtParser#parseTimeText} 认得的样子。
     *
     * <p>要处理两处：
     * <ul>
     *   <li>节次用逗号并列：{@code 第1,2节} → {@code 第1-2节}。
     *       原区间正则只认连字符，遇到逗号会落到单节匹配，
     *       把「2节」当成起点，整门课被标成「第2-2节」。</li>
     *   <li>周次用花括号：{@code {第1-12周}} → {@code (第1-12周)}。
     *       原周次正则只认圆括号，花括号匹配不到时会退化成
     *       「整句都当周次」，于是「周一」里的「一」被读成第 1 周。</li>
     * </ul>
     *
     * <p>只改这两种写法，其余文本原样保留。
     */
    static String normalizeTimeText(String timeText) {
        if (timeText == null || timeText.isBlank()) {
            return timeText;
        }
        return timeText
                .replaceAll("第\\s*(\\d+)\\s*[,，]\\s*(\\d+)\\s*节", "第$1-$2节")
                .replace('｛', '(').replace('｝', ')')
                .replace('{', '(').replace('}', ')');
    }

    private static int intAttr(Element element, String name, int fallback) {
        String value = element.attr(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
