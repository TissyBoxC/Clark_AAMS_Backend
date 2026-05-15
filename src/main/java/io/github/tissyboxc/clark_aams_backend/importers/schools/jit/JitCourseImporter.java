package io.github.tissyboxc.clark_aams_backend.importers.schools.jit;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.importers.SchoolCourseImporter;
import io.github.tissyboxc.clark_aams_backend.importers.dto.CourseDto;
import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseRequest;
import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JitCourseImporter implements SchoolCourseImporter {
    private final JitClient jitClient;

    public JitCourseImporter() {
        this.jitClient = new JitClient();
    }

    @Override
    public String schoolId() {
        return "jit";
    }

    @Override
    public ImportCourseResponse importCourses(ImportCourseRequest request) {
        if (request == null || request.loginSession() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        String html = jitClient.fetchCoursePage(request.loginSession());
        List<JitJwxtParser.JitRawCourse> rawCourses;
        try {
            rawCourses = JitJwxtParser.parseIndexChartsTable(html);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.COURSE_PARSE_FAILED, ErrorCode.COURSE_PARSE_FAILED.defaultMessage(), exception);
        }

        List<CourseDto> courses = new ArrayList<>();
        for (JitJwxtParser.JitRawCourse rawCourse : rawCourses) {
            List<Integer> weeks = rawCourse.weeks();
            Integer startWeek = weeks.isEmpty() ? null : weeks.get(0);
            Integer endWeek = weeks.isEmpty() ? null : weeks.get(weeks.size() - 1);

            courses.add(new CourseDto(
                    rawCourse.name(),
                    startWeek,
                    endWeek,
                    rawCourse.day(),
                    rawCourse.startSection() == null ? null : rawCourse.startSection(),
                    rawCourse.endSection() == null ? null : rawCourse.endSection(),
                    rawCourse.position(),
                    rawCourse.teacher(),
                    weeks,
                    rawCourse.rawTime()
            ));
        }

        return new ImportCourseResponse(
                schoolId(),
                1,
                courses,
                List.of()
        );
    }
}
