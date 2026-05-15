package io.github.tissyboxc.clark_aams_backend.pickup;

public record CoursePickupImageDto(
        String fileName,
        String originalName,
        String contentType,
        Long size
) {
}
