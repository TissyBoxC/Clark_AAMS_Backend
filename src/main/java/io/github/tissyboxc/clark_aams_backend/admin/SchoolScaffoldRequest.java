package io.github.tissyboxc.clark_aams_backend.admin;

import java.util.List;

public record SchoolScaffoldRequest(
        String schoolId,
        String classPrefix,
        String name,
        String shortName,
        Boolean enabled,
        Boolean academicImport,
        Boolean imageRecognition,
        String loginMode,
        String loginUrl,
        List<String> successUrlPatterns,
        Integer version,
        Boolean overwrite
) {
}
