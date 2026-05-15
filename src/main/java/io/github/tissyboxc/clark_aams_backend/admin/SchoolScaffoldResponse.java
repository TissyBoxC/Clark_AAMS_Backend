package io.github.tissyboxc.clark_aams_backend.admin;

import java.util.List;

public record SchoolScaffoldResponse(
        String schoolId,
        String packageName,
        String classPrefix,
        List<String> createdFiles
) {
}
