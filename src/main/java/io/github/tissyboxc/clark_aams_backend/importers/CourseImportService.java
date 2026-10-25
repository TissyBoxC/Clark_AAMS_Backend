package io.github.tissyboxc.clark_aams_backend.importers;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.importers.dto.HtmlImportCourseRequest;
import io.github.tissyboxc.clark_aams_backend.importers.dto.HtmlImportCourseResponse;
import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseRequest;
import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseResponse;
import io.github.tissyboxc.clark_aams_backend.importers.schools.SchoolAdapter;
import io.github.tissyboxc.clark_aams_backend.importers.schools.SchoolAdapterRegistry;
import io.github.tissyboxc.clark_aams_backend.importers.schools.UnsupportedSchoolHtmlStore;
import io.github.tissyboxc.clark_aams_backend.pickup.CoursePickupCreateResponse;
import io.github.tissyboxc.clark_aams_backend.pickup.CoursePickupRecordDto;
import io.github.tissyboxc.clark_aams_backend.pickup.CoursePickupStore;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class CourseImportService {
    private static final String SAVE_SUCCESS_MESSAGE = "\u4fdd\u5b58\u6210\u529f,\u8bf7\u7b49\u5f85\u6dfb\u52a0\u9002\u914d";

    private final SchoolAdapterRegistry schoolAdapterRegistry;
    private final UnsupportedSchoolHtmlStore unsupportedSchoolHtmlStore;
    private final CoursePickupStore pickupStore;
    private final ObjectMapper objectMapper;

    public CourseImportService(
            SchoolAdapterRegistry schoolAdapterRegistry,
            UnsupportedSchoolHtmlStore unsupportedSchoolHtmlStore,
            CoursePickupStore pickupStore,
            ObjectMapper objectMapper
    ) {
        this.schoolAdapterRegistry = schoolAdapterRegistry;
        this.unsupportedSchoolHtmlStore = unsupportedSchoolHtmlStore;
        this.pickupStore = pickupStore;
        this.objectMapper = objectMapper;
    }

    public ImportCourseResponse importCourses(String schoolId, ImportCourseRequest request) {
        if (isBlank(schoolId) || request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return schoolAdapterRegistry.getBySchoolId(schoolId).importCourses(request);
    }

    public HtmlImportResult importCoursesByHtml(HtmlImportCourseRequest request) {
        if (request == null || isBlank(request.html())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        SchoolAdapter adapter = resolveAdapter(request);
        if (adapter != null) {
            ImportCourseResponse response = adapter.importHtml(request.html());
            return HtmlImportResult.parsed(HtmlImportCourseResponse.parsed(
                    response.schoolId(),
                    adapter.school().name(),
                    response.importerVersion(),
                    response.courses(),
                    response.warnings()
            ));
        }

        String schoolName = firstNonBlank(request.schoolName(), request.school());
        if (schoolName.isBlank()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "school or schoolName is required when no parser is matched"
            );
        }

        var savedFile = unsupportedSchoolHtmlStore.save(schoolName, request.html());
        return HtmlImportResult.saved(
                SAVE_SUCCESS_MESSAGE,
                HtmlImportCourseResponse.saved(schoolName, savedFile.fileName())
        );
    }

    public CoursePickupCreateResponse importCoursesAndCreatePickup(String schoolId, ImportCourseRequest request) {
        ImportCourseResponse response = importCourses(schoolId, request);
        try {
            String jsonText = objectMapper.writeValueAsString(response.courses());
            CoursePickupRecordDto record = pickupStore.create(jsonText, List.of());
            return new CoursePickupCreateResponse(record.code(), response.courses());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.COURSE_PARSE_FAILED, "Failed to generate pickup JSON", exception);
        }
    }

    private SchoolAdapter resolveAdapter(HtmlImportCourseRequest request) {
        return schoolAdapterRegistry.find(firstNonBlank(request.school(), request.schoolName()))
                .orElse(null);
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? safeTrim(second) : safeTrim(first);
    }

    private boolean isBlank(String value) {
        return safeTrim(value).isBlank();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    public record HtmlImportResult(String message, HtmlImportCourseResponse response) {
        public static HtmlImportResult parsed(HtmlImportCourseResponse response) {
            return new HtmlImportResult("ok", response);
        }

        public static HtmlImportResult saved(String message, HtmlImportCourseResponse response) {
            return new HtmlImportResult(message, response);
        }
    }
}
