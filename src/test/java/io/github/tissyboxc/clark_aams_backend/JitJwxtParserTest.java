package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.importers.schools.jit.JitJwxtParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JIT 课表解析器单测。
 *
 * 覆盖真实教务系统课表里出现的各类周次/节次写法。这些用例是 P0-1 的验收基准：
 * 修复前 C1（多段周次只取第一段）、C2（节次正则误吞周次数字）相关用例应当是红的。
 */
class JitJwxtParserTest {

    @Nested
    @DisplayName("周次解析")
    class Weeks {

        @Test
        @DisplayName("单段周次：1-4周")
        void singleRange() {
            assertEquals(List.of(1, 2, 3, 4), JitJwxtParser.parseWeeks("1-4周"));
        }

        @Test
        @DisplayName("多段周次：1-3周,5-16周 必须全部保留（C1）")
        void multipleRanges() {
            assertEquals(
                    List.of(1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
                    JitJwxtParser.parseWeeks("第1-3周,第5-16周"));
        }

        @Test
        @DisplayName("多段周次带全角逗号：1-3周，5-6周")
        void multipleRangesFullWidthComma() {
            assertEquals(
                    List.of(1, 2, 3, 5, 6),
                    JitJwxtParser.parseWeeks("第1-3周，第5-6周"));
        }

        @Test
        @DisplayName("三段周次：1-2,4-5,7-8周")
        void threeRanges() {
            assertEquals(
                    List.of(1, 2, 4, 5, 7, 8),
                    JitJwxtParser.parseWeeks("1-2,4-5,7-8周"));
        }

        @Test
        @DisplayName("纯枚举周次：3,5,7周（原先整条丢失）")
        void discreteWeeks() {
            assertEquals(List.of(3, 5, 7), JitJwxtParser.parseWeeks("3,5,7周"));
        }

        @Test
        @DisplayName("单周周次：1-8周(单)")
        void oddWeeks() {
            assertEquals(List.of(1, 3, 5, 7), JitJwxtParser.parseWeeks("第1-8周(单)"));
        }

        @Test
        @DisplayName("双周周次：1-16周(双)")
        void evenWeeks() {
            assertEquals(
                    List.of(2, 4, 6, 8, 10, 12, 14, 16),
                    JitJwxtParser.parseWeeks("第1-16周(双)"));
        }

        @Test
        @DisplayName("单周 + 多段：1-8,10-12周(单)")
        void oddWithMultipleRanges() {
            assertEquals(List.of(1, 3, 5, 7, 11), JitJwxtParser.parseWeeks("1-8,10-12周(单)"));
        }

        @Test
        @DisplayName("横线可用波浪号：1~4周")
        void tildeRange() {
            assertEquals(List.of(1, 2, 3, 4), JitJwxtParser.parseWeeks("1~4周"));
        }

        @Test
        @DisplayName("空文本返回空列表")
        void empty() {
            assertEquals(List.of(), JitJwxtParser.parseWeeks(""));
            assertEquals(List.of(), JitJwxtParser.parseWeeks(null));
        }

        @Test
        @DisplayName("结果去重且升序")
        void dedupAndSorted() {
            // 1-3 与 2-5 并集是 1..5，再加单个 1 周，去重后仍为 1..5
            assertEquals(List.of(1, 2, 3, 4, 5), JitJwxtParser.parseWeeks("1-3,2-5,1周"));
        }
    }

    @Nested
    @DisplayName("节次解析")
    class Sections {

        @Test
        @DisplayName("1-2节(1-4周) 解析出节次与周次")
        void rangeWithWeeks() {
            JitJwxtParser.TimeInfo info = JitJwxtParser.parseTimeText("1-2节(1-4周)");
            assertEquals(1, info.startSection());
            assertEquals(2, info.endSection());
            assertEquals(List.of(1, 2, 3, 4), info.weeks());
        }

        @Test
        @DisplayName("含星期与周次的完整文本不得把周次数字当节次（C2）")
        void mustNotSwallowWeekNumbers() {
            JitJwxtParser.TimeInfo info = JitJwxtParser.parseTimeText("第1-3周,星期1,第7节-第8节");
            assertEquals(7, info.startSection(), "开始节次应为 7，而不是周次数字 1");
            assertEquals(8, info.endSection(), "结束节次应为 8");
        }

        @Test
        @DisplayName("单节：第5节")
        void singleSection() {
            JitJwxtParser.TimeInfo info = JitJwxtParser.parseTimeText("第5节");
            assertEquals(5, info.startSection());
            assertEquals(5, info.endSection());
        }

        @Test
        @DisplayName("节次区间带周次括号：3-4节(1-16周)")
        void sectionRangeWithWeeksSuffix() {
            JitJwxtParser.TimeInfo info = JitJwxtParser.parseTimeText("3-4节(1-16周)");
            assertEquals(3, info.startSection());
            assertEquals(4, info.endSection());
            assertEquals(16, info.weeks().size());
        }

        @Test
        @DisplayName("只有周次没有节次时，节次为空")
        void weeksOnly() {
            JitJwxtParser.TimeInfo info = JitJwxtParser.parseTimeText("1-16周");
            assertEquals(null, info.startSection());
            assertEquals(null, info.endSection());
            assertEquals(16, info.weeks().size());
        }
    }

    @Nested
    @DisplayName("完整表格解析")
    class Table {

        private static final String TABLE = """
                <html><body>
                  <table id="Table6">
                    <tbody>
                      <tr>
                        <td>星期一</td>
                        <td>
                          <p class="kblsbk">
                            <span class="time">1-2节(1-3周,5-16周)</span>
                            <span class="kejie">高等数学</span>
                            <span class="didian">A101</span>
                            <span class="teacher">张三</span>
                          </p>
                        </td>
                      </tr>
                      <tr>
                        <td>星期二</td>
                        <td>
                          <p class="kblsbk">
                            <span class="time">3-4节(1-16周)</span>
                            <span class="kejie">大学英语</span>
                            <span class="didian">B202</span>
                            <span class="teacher">李四</span>
                          </p>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </body></html>
                """;

        @Test
        @DisplayName("解析出两门课，且多段周次完整保留")
        void parsesAllCourses() {
            List<JitJwxtParser.JitRawCourse> courses = JitJwxtParser.parseIndexChartsTable(TABLE);
            assertEquals(2, courses.size());

            JitJwxtParser.JitRawCourse math = courses.stream()
                    .filter(c -> c.name().equals("高等数学"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(1, math.day());
            assertEquals(1, math.startSection());
            assertEquals(2, math.endSection());
            assertEquals("A101", math.position());
            assertEquals("张三", math.teacher());
            assertEquals(
                    List.of(1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
                    math.weeks(),
                    "1-3周,5-16周 必须完整展开");
        }

        @Test
        @DisplayName("空表格返回空列表而不是抛错以外的异常类型")
        void emptyTable() {
            String html = """
                    <html><body>
                      <table id="Table6"><tbody>
                        <tr><td>星期一</td><td></td></tr>
                      </tbody></table>
                    </body></html>
                    """;
            List<JitJwxtParser.JitRawCourse> courses = JitJwxtParser.parseIndexChartsTable(html);
            assertNotNull(courses);
            assertTrue(courses.isEmpty());
        }
    }
}
