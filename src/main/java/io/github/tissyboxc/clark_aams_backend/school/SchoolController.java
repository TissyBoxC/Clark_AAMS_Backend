package io.github.tissyboxc.clark_aams_backend.school;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/schools", produces = "application/json;charset=UTF-8")
public class SchoolController {
    private final SchoolService schoolService;

    public SchoolController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @GetMapping
    public ApiResponse<List<SchoolDto>> listSchools() {
        return ApiResponse.ok(schoolService.listEnabledSchools());
    }

    @GetMapping("/{schoolId}")
    public ApiResponse<SchoolDto> getSchool(@PathVariable String schoolId) {
        return ApiResponse.ok(schoolService.getSchool(schoolId));
    }

    @GetMapping("/{schoolId}/lesson-times")
    public ApiResponse<SchoolLessonTimesDto> getSchoolLessonTimes(@PathVariable String schoolId) {
        return ApiResponse.ok(schoolService.getLessonTimes(schoolId));
    }

    @GetMapping("/lesson-time-profiles")
    public ApiResponse<List<SchoolLessonTimesDto>> listLessonTimeProfiles() {
        return ApiResponse.ok(schoolService.listLessonTimeProfiles());
    }
}
