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
            rawCourses = JitJwxtParser.parseIndexChartsTable(html);
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ErrorCode.COURSE_PARSE_FAILED,
                    ErrorCode.COURSE_PARSE_FAILED.defaultMessage(),
                    exception
            );
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
