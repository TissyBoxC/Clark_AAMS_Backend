package io.github.tissyboxc.clark_aams_backend.importers.schools.jit;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.importers.dto.CourseDto;
import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseRequest;
import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseResponse;
import io.github.tissyboxc.clark_aams_backend.importers.dto.WeekPattern;
import io.github.tissyboxc.clark_aams_backend.importers.schools.SchoolAdapter;
import io.github.tissyboxc.clark_aams_backend.school.SchoolEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JitSchoolAdapter implements SchoolAdapter {
    private final JitClient jitClient;

    public JitSchoolAdapter(JitClient jitClient) {
        this.jitClient = jitClient;
    }

    @Override
    public SchoolEntity school() {
        return new SchoolEntity(
                "jit",
                "\u91d1\u9675\u79d1\u6280\u5b66\u9662",
                "JIT",
                true,
                true,
                false,
                "webview",
                "https://jwxt.jit.edu.cn/default2.aspx",
                new String[]{"https://jwxt.jit.edu.cn/xs_main.aspx*"},
                1
        );
    }

    @Override
    public ImportCourseResponse importCourses(ImportCourseRequest request) {
        if (request == null || request.loginSession() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        String html = jitClient.fetchCoursePage(request.loginSession());
        return importHtml(html);
    }

    @Override
    public ImportCourseResponse importHtml(String html) {
        List<JitJwxtParser.JitRawCourse> rawCourses;
        try {
            /*
             * 两种页面形态都要认：
             *   块格式 table#Table6 + p.kblsbk —— 早期对接的模板
             *   格子格式 table#Table1.schedule —— 用 <br> 分行的纯文本格子
             * 先试块格式，解不出再试格子格式。
             */
            rawCourses = JitJwxtParser.parseIndexChartsTable(html);
            if (rawCourses.isEmpty()) {
                rawCourses = JitGridTableParser.parse(html);
            }
        } catch (RuntimeException exception) {
            // 块格式崩了不代表页面错，可能只是这次是格子格式
            try {
                rawCourses = JitGridTableParser.parse(html);
            } catch (RuntimeException ignored) {
                throw new BusinessException(
                        ErrorCode.COURSE_PARSE_FAILED,
                        ErrorCode.COURSE_PARSE_FAILED.defaultMessage(),
                        exception
                );
            }
        }

        List<CourseDto> courses = new ArrayList<>();
        for (JitJwxtParser.JitRawCourse rawCourse : rawCourses) {
            // 用离散周次反推「区间 + 单双周」，而不是简单取首末两项。
            // 取首末项会把 1-3,5-16 压成 1..16、把 1-16(单) 压成 1..15。
            WeekPattern pattern = WeekPattern.fromWeeks(rawCourse.weeks());

            courses.add(CourseDto.of(
                    rawCourse.name(),
                    rawCourse.day(),
                    rawCourse.startSection(),
                    rawCourse.endSection(),
                    rawCourse.position(),
                    rawCourse.teacher(),
                    rawCourse.rawTime(),
                    pattern
            ));
        }

        return new ImportCourseResponse(
                schoolId(),
                school().version(),
                courses,
                List.of()
        );
    }
}
