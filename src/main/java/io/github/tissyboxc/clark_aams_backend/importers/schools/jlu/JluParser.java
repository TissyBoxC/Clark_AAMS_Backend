package io.github.tissyboxc.clark_aams_backend.importers.schools.jlu;

import io.github.tissyboxc.clark_aams_backend.importers.dto.CourseDto;
import io.github.tissyboxc.clark_aams_backend.importers.dto.WeekPattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

public final class JluParser {
    private JluParser() {
    }

    public static List<CourseDto> parseCourses(String html) {
        Element table = selectTable(html);
        if (table == null) {
            throw new IllegalArgumentException("JLU course table table.wut_table not found");
        }

        Element tbody = table.selectFirst("tbody");
        if (tbody == null) {
            throw new IllegalArgumentException("JLU course table tbody not found");
        }

        List<CourseDto> courses = new ArrayList<>();
        for (Element tr : tbody.children()) {
            if (!"tr".equals(tr.tagName())) {
                continue;
            }
            List<Element> tds = tr.children().stream()
                    .filter(element -> "td".equals(element.tagName()))
                    .toList();
            for (int index = 1; index < tds.size(); index++) {
                courses.addAll(parseCourseTd(tds.get(index)));
            }
        }
        return courses;
    }

    private static List<CourseDto> parseCourseTd(Element td) {
        List<CourseDto> courses = new ArrayList<>();
        Integer day = parseInteger(td.attr("data-week"));

        for (Element item : td.select("div.mtt_arrange_item")) {
            Element nameTag = item.selectFirst("div.mtt_item_kcmc");
            Element teacherTag = item.selectFirst("div.mtt_item_jxbmc");
            Element roomTag = item.selectFirst("div.mtt_item_room");
            if (nameTag == null || roomTag == null) {
                continue;
            }

            String courseName = nameTag.text().replaceFirst("\\[.*$", "").trim();
            if (courseName.isBlank()) {
                continue;
            }
            String teacher = teacherTag == null ? "" : teacherTag.text().trim();
            String roomText = roomTag.text().trim();

            RoomInfo roomInfo = parseRoomText(roomText);
            List<Integer> weeks = parseWeeks(roomInfo.weeksPart());
            SectionInfo sectionInfo = parseSection(roomInfo.sectionPart());

            courses.add(CourseDto.of(
                    courseName,
                    day,
                    sectionInfo.startSection(),
                    sectionInfo.endSection(),
                    roomInfo.position(),
                    teacher,
                    roomInfo.rawTime(),
                    WeekPattern.fromWeeks(weeks)
            ));
        }

        return courses;
    }

    private static RoomInfo parseRoomText(String roomText) {
        String weeksPart = "";
        String sectionPart = "";
        String position = "";

        for (String part : roomText.split(",")) {
            String value = part.trim();
            if (value.contains("周")) {
                weeksPart = value;
            } else if (value.contains("第") && value.contains("节")) {
                sectionPart = value;
            } else {
                position = value;
            }
        }

        String rawTime = roomText;
        if (!position.isBlank() && rawTime.endsWith(position)) {
            rawTime = rawTime.substring(0, rawTime.length() - position.length());
        }
        rawTime = rawTime.replaceFirst(",+$", "").trim();
        return new RoomInfo(weeksPart, sectionPart, position, rawTime);
    }

    private static List<Integer> parseWeeks(String weeksPart) {
        List<Integer> weeks = new ArrayList<>();
        for (String item : weeksPart.split(",")) {
            String value = item.replace("周", "").replace("第", "").trim();
            if (value.contains("-")) {
                String[] range = value.split("-", 2);
                Integer start = parseInteger(range[0]);
                Integer end = parseInteger(range[1]);
                if (start != null && end != null) {
                    for (int week = start; week <= end; week++) {
                        weeks.add(week);
                    }
                }
            } else {
                Integer week = parseInteger(value);
                if (week != null) {
                    weeks.add(week);
                }
            }
        }
        return weeks;
    }

    private static SectionInfo parseSection(String sectionPart) {
        if (sectionPart.isBlank()) {
            return new SectionInfo(null, null);
        }
        String[] sections = sectionPart.replace("第", "").replace("节", "").split("-");
        if (sections.length == 2) {
            return new SectionInfo(parseInteger(sections[0]), parseInteger(sections[1]));
        }
        if (sections.length == 1) {
            Integer section = parseInteger(sections[0]);
            return new SectionInfo(section, section);
        }
        return new SectionInfo(null, null);
    }

    private static Element selectTable(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Document document = Jsoup.parse(html);
        Element table = document.selectFirst("table.wut_table");
        if (table == null && document.body() != null && document.body().childrenSize() > 0) {
            Element firstChild = document.body().child(0);
            if ("table".equalsIgnoreCase(firstChild.tagName())) {
                table = firstChild;
            }
        }
        return table;
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record RoomInfo(String weeksPart, String sectionPart, String position, String rawTime) {
    }

    private record SectionInfo(Integer startSection, Integer endSection) {
    }
}
