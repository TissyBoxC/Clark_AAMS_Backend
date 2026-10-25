package io.github.tissyboxc.clark_aams_backend.admin;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class SchoolScaffoldService {
    private static final String BASE_PACKAGE = "io.github.tissyboxc.clark_aams_backend.importers.schools";
    private static final Path SCHOOL_BASE_DIR = Path.of(
            "src", "main", "java", "io", "github", "tissyboxc",
            "clark_aams_backend", "importers", "schools"
    );
    private static final Pattern SCHOOL_ID_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern JAVA_IDENTIFIER_PATTERN = Pattern.compile("[A-Z][A-Za-z0-9]*");

    public SchoolScaffoldResponse scaffold(SchoolScaffoldRequest request) {
        SchoolScaffoldData data = normalize(request);
        Path packageDir = SCHOOL_BASE_DIR.resolve(data.schoolId()).normalize();
        Path expectedBase = SCHOOL_BASE_DIR.toAbsolutePath().normalize();
        Path targetDir = packageDir.toAbsolutePath().normalize();
        if (!targetDir.startsWith(expectedBase)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "invalid school id");
        }

        List<GeneratedFile> generatedFiles = List.of(
                new GeneratedFile(data.classPrefix() + "SchoolAdapter.java", adapterTemplate(data)),
                new GeneratedFile(data.classPrefix() + "Client.java", clientTemplate(data)),
                new GeneratedFile(data.classPrefix() + "Parser.java", parserTemplate(data))
        );

        boolean overwrite = Boolean.TRUE.equals(request.overwrite());
        List<String> createdFiles = new ArrayList<>();
        try {
            Files.createDirectories(packageDir);
            for (GeneratedFile generatedFile : generatedFiles) {
                Path file = packageDir.resolve(generatedFile.fileName());
                if (Files.exists(file) && !overwrite) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "file already exists: " + file);
                }
                Files.writeString(file, generatedFile.content(), StandardCharsets.UTF_8);
                createdFiles.add(file.toAbsolutePath().normalize().toString());
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to generate school scaffold", exception);
        }

        return new SchoolScaffoldResponse(
                data.schoolId(),
                data.packageName(),
                data.classPrefix(),
                createdFiles
        );
    }

    private SchoolScaffoldData normalize(SchoolScaffoldRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        String schoolId = safeTrim(request.schoolId()).toLowerCase(Locale.ROOT);
        if (!SCHOOL_ID_PATTERN.matcher(schoolId).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "invalid schoolId");
        }

        String classPrefix = safeTrim(request.classPrefix());
        if (classPrefix.isBlank()) {
            classPrefix = toClassPrefix(schoolId);
        }
        if (!JAVA_IDENTIFIER_PATTERN.matcher(classPrefix).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "invalid classPrefix");
        }

        String name = requireText(request.name(), "name is required");
        String shortName = requireText(request.shortName(), "shortName is required");
        String loginMode = safeTrim(request.loginMode()).isBlank() ? "webview" : safeTrim(request.loginMode());
        String loginUrl = requireText(request.loginUrl(), "loginUrl is required");
        List<String> successUrlPatterns = request.successUrlPatterns() == null
                ? List.of()
                : request.successUrlPatterns().stream().map(this::safeTrim).filter(pattern -> !pattern.isBlank()).toList();
        if (successUrlPatterns.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "successUrlPatterns must not be empty");
        }

        return new SchoolScaffoldData(
                schoolId,
                BASE_PACKAGE + "." + schoolId,
                classPrefix,
                name,
                shortName,
                request.enabled() == null || request.enabled(),
                request.academicImport() == null || request.academicImport(),
                Boolean.TRUE.equals(request.imageRecognition()),
                loginMode,
                loginUrl,
                successUrlPatterns,
                request.version() == null ? 1 : request.version()
        );
    }

    private String adapterTemplate(SchoolScaffoldData data) {
        return """
                package %s;

                import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
                import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
                import io.github.tissyboxc.clark_aams_backend.importers.dto.CourseDto;
                import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseRequest;
                import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseResponse;
                import io.github.tissyboxc.clark_aams_backend.importers.schools.SchoolAdapter;
                import io.github.tissyboxc.clark_aams_backend.school.SchoolEntity;
                import org.springframework.stereotype.Component;

                import java.util.List;

                @Component
                public class %sSchoolAdapter implements SchoolAdapter {
                    private final %sClient client;

                    public %sSchoolAdapter(%sClient client) {
                        this.client = client;
                    }

                    @Override
                    public SchoolEntity school() {
                        return new SchoolEntity(
                                %s,
                                %s,
                                %s,
                                %s,
                                %s,
                                %s,
                                %s,
                                %s,
                                new String[]{%s},
                                %d
                        );
                    }

                    @Override
                    public ImportCourseResponse importCourses(ImportCourseRequest request) {
                        if (request == null || request.loginSession() == null) {
                            throw new BusinessException(ErrorCode.BAD_REQUEST);
                        }

                        String html = client.fetchCoursePage(request.loginSession());
                        return importHtml(html);
                    }

                    @Override
                    public ImportCourseResponse importHtml(String html) {
                        List<CourseDto> courses = %sParser.parseCourses(html);
                        return new ImportCourseResponse(schoolId(), school().version(), courses, List.of());
                    }
                }
                """.formatted(
                data.packageName(),
                data.classPrefix(),
                data.classPrefix(),
                data.classPrefix(),
                data.classPrefix(),
                javaString(data.schoolId()),
                javaString(data.name()),
                javaString(data.shortName()),
                data.enabled(),
                data.academicImport(),
                data.imageRecognition(),
                javaString(data.loginMode()),
                javaString(data.loginUrl()),
                javaStringArrayItems(data.successUrlPatterns()),
                data.version(),
                data.classPrefix()
        );
    }

    private String clientTemplate(SchoolScaffoldData data) {
        return """
                package %s;

                import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
                import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
                import io.github.tissyboxc.clark_aams_backend.importers.dto.LoginSessionDto;
                import org.springframework.stereotype.Component;

                @Component
                public class %sClient {
                    public String fetchCoursePage(LoginSessionDto loginSession) {
                        if (loginSession.extra() != null) {
                            Object html = loginSession.extra().get("html");
                            if (html instanceof String value && !value.isBlank()) {
                                return value;
                            }
                        }

                        throw new BusinessException(
                                ErrorCode.ACADEMIC_REQUEST_FAILED,
                                "implement %sClient.fetchCoursePage"
                        );
                    }
                }
                """.formatted(data.packageName(), data.classPrefix(), data.classPrefix());
    }

    private String parserTemplate(SchoolScaffoldData data) {
        return """
                package %s;

                import io.github.tissyboxc.clark_aams_backend.importers.dto.CourseDto;

                import java.util.List;

                public final class %sParser {
                    private %sParser() {
                    }

                    public static List<CourseDto> parseCourses(String html) {
                        // TODO: convert school html/json into CourseDto list
                        return List.of();
                    }
                }
                """.formatted(data.packageName(), data.classPrefix(), data.classPrefix());
    }

    private String javaStringArrayItems(List<String> values) {
        return values.stream().map(this::javaString).reduce((left, right) -> left + ", " + right).orElse("");
    }

    private String javaString(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n") + "\"";
    }

    private String toClassPrefix(String schoolId) {
        StringBuilder builder = new StringBuilder();
        for (String part : schoolId.split("_")) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private String requireText(String value, String message) {
        String trimmed = safeTrim(value);
        if (trimmed.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
        return trimmed;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private record GeneratedFile(String fileName, String content) {
    }

    private record SchoolScaffoldData(
            String schoolId,
            String packageName,
            String classPrefix,
            String name,
            String shortName,
            boolean enabled,
            boolean academicImport,
            boolean imageRecognition,
            String loginMode,
            String loginUrl,
            List<String> successUrlPatterns,
            int version
    ) {
    }
}
