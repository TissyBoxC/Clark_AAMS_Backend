package io.github.tissyboxc.clark_aams_backend.importers.schools.jlu;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.importers.dto.CourseDto;
import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseRequest;
import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseResponse;
import io.github.tissyboxc.clark_aams_backend.importers.schools.SchoolAdapter;
import io.github.tissyboxc.clark_aams_backend.school.SchoolEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JluSchoolAdapter implements SchoolAdapter {
    private final JluClient jluClient;

    public JluSchoolAdapter(JluClient jluClient) {
        this.jluClient = jluClient;
    }

    @Override
    public SchoolEntity school() {
        return new SchoolEntity(
                "jlu",
                "\u5409\u6797\u5927\u5b66",
                "JLU",
                true,
                true,
                false,
                "webview",
                "https://iedu.jlu.edu.cn",
                new String[]{"https://iedu.jlu.edu.cn/jwapp/*"},
                1
        );
    }

    @Override
    public ImportCourseResponse importCourses(ImportCourseRequest request) {
        if (request == null || request.loginSession() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        String html = jluClient.fetchCoursePage(request.loginSession());
        return importHtml(html);
    }

    @Override
    public ImportCourseResponse importHtml(String html) {
        List<CourseDto> courses;
        try {
            courses = JluParser.parseCourses(html);
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ErrorCode.COURSE_PARSE_FAILED,
                    ErrorCode.COURSE_PARSE_FAILED.defaultMessage(),
                    exception
            );
        }

        return new ImportCourseResponse(
                schoolId(),
                school().version(),
                courses,
                List.of()
        );
    }
}
