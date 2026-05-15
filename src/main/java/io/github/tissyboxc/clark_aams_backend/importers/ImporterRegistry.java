package io.github.tissyboxc.clark_aams_backend.importers;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ImporterRegistry {
    private final Map<String, SchoolCourseImporter> importers;

    public ImporterRegistry(List<SchoolCourseImporter> importerList) {
        this.importers = importerList.stream()
                .collect(Collectors.toUnmodifiableMap(
                        importer -> normalize(importer.schoolId()),
                        Function.identity()
                ));
    }

    public SchoolCourseImporter get(String schoolId) {
        SchoolCourseImporter importer = importers.get(normalize(schoolId));
        if (importer == null) {
            throw new BusinessException(ErrorCode.IMPORTER_NOT_FOUND);
        }
        return importer;
    }

    private String normalize(String schoolId) {
        return schoolId == null ? "" : schoolId.trim().toLowerCase(Locale.ROOT);
    }
}
