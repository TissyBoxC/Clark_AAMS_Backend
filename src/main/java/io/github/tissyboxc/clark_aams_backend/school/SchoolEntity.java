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
            String importerKey,
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
                importerKey,
                "general-a",
                version
        );
    }
}
