package io.github.tissyboxc.clark_aams_backend.importers.schools.jit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 用真实教务系统页面验证格子表格解析。
 *
 * <p>样本在 {@code src/test/resources/jit-grid/} 下，从用户实际课表页复制：
 * 一段是 iframe 里的课表本体，一段是外层框架页（只有 iframe，没有课表）。
 */
class JitGridRealPageTests {

    private static String read(String name) throws Exception {
        return Files.readString(Path.of("src/test/resources/jit-grid", name), StandardCharsets.UTF_8);
    }

    @Test
    void parsesRealSchedulePage() throws Exception {
        String html = read("schedule-body.html");
        List<JitJwxtParser.JitRawCourse> courses = JitGridTableParser.parse(html);

        System.out.println("=== 解析出课程数: " + courses.size() + " ===");
        for (JitJwxtParser.JitRawCourse c : courses) {
            System.out.printf("  %s | 周%d | 第%s-%s节 | %s | %s | %s%n",
                    c.name(), c.day(), c.startSection(), c.endSection(),
                    c.weeks(), c.teacher(), c.position());
        }

        Assertions.assertFalse(courses.isEmpty(), "应当解析出课程");

        JitJwxtParser.JitRawCourse first = courses.stream()
                .filter(c -> c.name().contains("安卓系统开发"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("没解出「安卓系统开发」"));
        Assertions.assertEquals(1, first.day(), "应为周一");
        Assertions.assertEquals(1, first.startSection(), "应从第1节开始");
        // 页面把第1,2节和第3,4节分成两格，合并后是第1-4节
        Assertions.assertEquals(4, first.endSection(), "连堂应并到第4节");
    }

    @Test
    void mergesAdjacentSameCourseIntoOneBlock() throws Exception {
        String html = read("schedule-body.html");
        List<JitJwxtParser.JitRawCourse> courses = JitGridTableParser.parse(html);

        List<JitJwxtParser.JitRawCourse> physics = courses.stream()
                .filter(c -> c.name().contains("大学物理"))
                .toList();

        System.out.println("=== 大学物理C 合并后 ===");
        for (JitJwxtParser.JitRawCourse c : physics) {
            System.out.printf("  周%d 第%s-%s节 %s%n", c.day(), c.startSection(), c.endSection(), c.weeks());
        }

        // 教务系统把周日 1,2 / 3,4 / 5,6 / 7,8 拆成四个格子，
        // 合并后应是「周日 第1-8节」一条，而不是四条。
        Assertions.assertEquals(1, physics.size(),
                "连着上的同一门课应合并成一条，实际 " + physics.size() + " 条");
        JitJwxtParser.JitRawCourse one = physics.get(0);
        Assertions.assertEquals(7, one.day(), "周日");
        Assertions.assertEquals(1, one.startSection());
        Assertions.assertEquals(8, one.endSection(), "第1节连到第8节");
    }

    @Test
    void outerFramePageHasNoCourses() throws Exception {
        String html = read("frame-outer.html");
        List<JitJwxtParser.JitRawCourse> courses = JitGridTableParser.parse(html);
        System.out.println("外层页面解析出 " + courses.size() + " 门（应为 0）");
        Assertions.assertTrue(courses.isEmpty(), "外层框架页本身没有课表");
    }
}
