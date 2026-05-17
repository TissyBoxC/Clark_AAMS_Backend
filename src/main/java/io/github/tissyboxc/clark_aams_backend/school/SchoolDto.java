package io.github.tissyboxc.clark_aams_backend.school;

public record SchoolDto(
        String id,
        String name,
        String shortName,
        boolean enabled,
        SchoolCapabilityDto capabilities,
        SchoolLoginDto login,
        String lessonTimeProfile,
        int version
) {
}
