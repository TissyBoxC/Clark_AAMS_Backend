package io.github.tissyboxc.clark_aams_backend.pickup;

import java.util.List;

public record CoursePickupRecordDto(
        String code,
        String jsonText,
        List<CoursePickupImageDto> images,
        String createdAt,
        String updatedAt
) {
}
