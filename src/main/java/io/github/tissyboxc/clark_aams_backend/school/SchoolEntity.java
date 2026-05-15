package io.github.tissyboxc.clark_aams_backend.school;

public record SchoolEntity(
        String id,
        String name,
        String shortName,
        boolean enabled,
        boolean academicImportEnabled,
        boolean imageRecognitionEnabled,
        String loginMode,
        String loginUrl,
        String[] successUrlPatterns,
        String importerKey,
        int version
) {
}
