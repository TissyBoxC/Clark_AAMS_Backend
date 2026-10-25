package io.github.tissyboxc.clark_aams_backend.importers.schools;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class UnsupportedSchoolHtmlStore {
    private static final DateTimeFormatter SUFFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final Path storageDir;

    public UnsupportedSchoolHtmlStore(
            @Value("${clark-aams.import.unsupported-html-dir:unsupported-school-html}") String storageDir
    ) {
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
    }

    public SavedHtmlFile save(String schoolName, String html) {
        String normalizedSchoolName = safeTrim(schoolName);
        String normalizedHtml = safeTrim(html);
        if (normalizedSchoolName.isBlank() || normalizedHtml.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        try {
            Files.createDirectories(storageDir);
            Path targetFile = resolveTargetFile(normalizedSchoolName);
            Files.writeString(targetFile, normalizedHtml, StandardCharsets.UTF_8);
            return new SavedHtmlFile(targetFile.getFileName().toString());
        } catch (IOException exception) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to save unsupported school html",
                    exception
            );
        }
    }

    private Path resolveTargetFile(String schoolName) {
        String baseName = sanitizeFileName(schoolName);
        Path target = storageDir.resolve(baseName + ".html").normalize();
        if (!Files.exists(target)) {
            return target;
        }
        String suffix = LocalDateTime.now().format(SUFFIX_FORMATTER);
        return storageDir.resolve(baseName + "-" + suffix + ".html").normalize();
    }

    private String sanitizeFileName(String schoolName) {
        StringBuilder builder = new StringBuilder();
        for (char ch : schoolName.toCharArray()) {
            if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_') {
                builder.append(ch);
                continue;
            }
            Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B) {
                builder.append(ch);
                continue;
            }
            if (!Character.isWhitespace(ch)) {
                builder.append('_');
            }
        }
        String sanitized = builder.toString().trim();
        if (sanitized.isBlank()) {
            sanitized = "school-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT));
        }
        return sanitized;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    public record SavedHtmlFile(String fileName) {
    }
}
