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
        String lessonTimeProfile,
        int version
) {
    public SchoolEntity(
            String id,
            String name,
            String shortName,
            boolean enabled,
            boolean academicImportEnabled,
            boolean imageRecognitionEnabled,
            String loginMode,
            String loginUrl,
            String[] successUrlPatterns,
            int version
    ) {
        this(
                id,
                name,
                shortName,
                enabled,
                academicImportEnabled,
                imageRecognitionEnabled,
                loginMode,
                loginUrl,
                successUrlPatterns,
                "general-a",
                version
        );
    }
}
