package io.github.tissyboxc.clark_aams_backend.pickup;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class CoursePickupStore {
    private static final String INDEX_FILE = "index.json";
    private static final String IMAGE_DIR = "images";
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();
    private final String configuredStorageDir;
    private Path storageDir;
    private Path indexPath;
    private Map<String, CoursePickupRecordDto> records = new LinkedHashMap<>();

    public CoursePickupStore(
            ObjectMapper objectMapper,
            @Value("${clark-aams.pickup.storage-dir:}") String configuredStorageDir
    ) {
        this.objectMapper = objectMapper;
        this.configuredStorageDir = configuredStorageDir;
    }

    @PostConstruct
    public void initialize() {
        storageDir = resolveStorageDir();
        indexPath = storageDir.resolve(INDEX_FILE);
        try {
            Files.createDirectories(storageDir.resolve(IMAGE_DIR));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to create pickup storage dir: " + storageDir, exception);
        }
        if (Files.exists(indexPath)) {
            records = readIndex();
        } else {
            writeIndex();
        }
    }

    public synchronized CoursePickupRecordDto create(String jsonText, List<MultipartFile> images) {
        validateJsonArray(jsonText);
        String code = nextCode();
        String now = now();
        List<CoursePickupImageDto> savedImages = saveImages(code, images);
        CoursePickupRecordDto record = new CoursePickupRecordDto(code, jsonText, savedImages, now, now);
        records.put(code, record);
        writeIndex();
        return record;
    }

    public synchronized Optional<CoursePickupRecordDto> find(String code) {
        return Optional.ofNullable(records.get(normalizeCode(code)));
    }

    public synchronized List<CoursePickupSummaryDto> list() {
        return records.values().stream()
                .sorted(Comparator.comparing(CoursePickupRecordDto::createdAt).reversed())
                .map(record -> new CoursePickupSummaryDto(
                        record.code(),
                        record.images() == null ? 0 : record.images().size(),
                        record.createdAt(),
                        record.updatedAt()
                ))
                .toList();
    }

    public synchronized CoursePickupRecordDto updateJson(String code, String jsonText) {
        validateJsonArray(jsonText);
        CoursePickupRecordDto current = requireRecord(code);
        CoursePickupRecordDto updated = new CoursePickupRecordDto(
                current.code(),
                jsonText,
                normalizeImages(current.images()),
                current.createdAt(),
                now()
        );
        records.put(updated.code(), updated);
        writeIndex();
        return updated;
    }

    public synchronized void delete(String code) {
        CoursePickupRecordDto removed = records.remove(normalizeCode(code));
        if (removed == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "取件码不存在");
        }
        deleteDirectory(imageDir(removed.code()));
        writeIndex();
    }

    public synchronized void deleteImage(String code, String fileName) {
        CoursePickupRecordDto current = requireRecord(code);
        String normalizedFileName = safeFileName(fileName);
        CoursePickupImageDto target = normalizeImages(current.images()).stream()
                .filter(image -> image.fileName().equals(normalizedFileName))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "图片不存在"));
        try {
            Files.deleteIfExists(imageDir(current.code()).resolve(target.fileName()));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to delete pickup image", exception);
        }
        List<CoursePickupImageDto> images = normalizeImages(current.images()).stream()
                .filter(image -> !image.fileName().equals(target.fileName()))
                .toList();
        CoursePickupRecordDto updated = new CoursePickupRecordDto(
                current.code(),
                current.jsonText(),
                images,
                current.createdAt(),
                now()
        );
        records.put(updated.code(), updated);
        writeIndex();
    }

    public synchronized Path imagePath(String code, String fileName) {
        CoursePickupRecordDto current = requireRecord(code);
        String normalizedFileName = safeFileName(fileName);
        boolean existsInRecord = normalizeImages(current.images()).stream()
                .anyMatch(image -> image.fileName().equals(normalizedFileName));
        if (!existsInRecord) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片不存在");
        }
        Path path = imageDir(current.code()).resolve(normalizedFileName).toAbsolutePath().normalize();
        if (!path.startsWith(imageDir(current.code()).toAbsolutePath().normalize())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return path;
    }

    public Path storageDir() {
        return storageDir;
    }

    private CoursePickupRecordDto requireRecord(String code) {
        CoursePickupRecordDto record = records.get(normalizeCode(code));
        if (record == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "取件码不存在");
        }
        return record;
    }

    private List<CoursePickupImageDto> saveImages(String code, List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        Path targetDir = imageDir(code);
        try {
            Files.createDirectories(targetDir);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to create pickup image dir: " + targetDir, exception);
        }
        List<CoursePickupImageDto> savedImages = new ArrayList<>();
        int index = 1;
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty()) {
                continue;
            }
            String originalName = image.getOriginalFilename() == null ? "" : image.getOriginalFilename();
            String contentType = image.getContentType() == null ? "application/octet-stream" : image.getContentType();
            String fileName = "%02d%s".formatted(index++, extension(originalName, contentType));
            Path target = targetDir.resolve(fileName).toAbsolutePath().normalize();
            if (!target.startsWith(targetDir.toAbsolutePath().normalize())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            try (var inputStream = image.getInputStream()) {
                Files.copy(inputStream, target);
            } catch (IOException exception) {
                throw new UncheckedIOException("Failed to save pickup image", exception);
            }
            savedImages.add(new CoursePickupImageDto(fileName, originalName, contentType, image.getSize()));
        }
        return savedImages;
    }

    private Map<String, CoursePickupRecordDto> readIndex() {
        try {
            CoursePickupRecordDto[] values = objectMapper.readValue(indexPath.toFile(), CoursePickupRecordDto[].class);
            Map<String, CoursePickupRecordDto> result = new LinkedHashMap<>();
            if (values != null) {
                for (CoursePickupRecordDto value : values) {
                    if (value != null && value.code() != null && !value.code().isBlank()) {
                        result.put(normalizeCode(value.code()), new CoursePickupRecordDto(
                                normalizeCode(value.code()),
                                value.jsonText() == null ? "[]" : value.jsonText(),
                                normalizeImages(value.images()),
                                value.createdAt() == null ? now() : value.createdAt(),
                                value.updatedAt() == null ? now() : value.updatedAt()
                        ));
                    }
                }
            }
            return result;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to read pickup index: " + indexPath, exception);
        }
    }

    private void writeIndex() {
        try {
            Files.createDirectories(storageDir);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(indexPath.toFile(), records.values());
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write pickup index: " + indexPath, exception);
        }
    }

    private void validateJsonArray(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "JSON 不能为空");
        }
        try {
            Object parsed = objectMapper.readValue(jsonText, Object.class);
            if (!(parsed instanceof List<?>)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "JSON 必须是数组");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "JSON 格式错误", exception);
        }
    }

    private String nextCode() {
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder builder = new StringBuilder(CODE_LENGTH);
            for (int index = 0; index < CODE_LENGTH; index++) {
                builder.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            String code = builder.toString();
            if (!records.containsKey(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Failed to allocate pickup code");
    }

    private Path resolveStorageDir() {
        if (configuredStorageDir != null && !configuredStorageDir.isBlank()) {
            return Path.of(configuredStorageDir.trim()).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.dir")).resolve("course-pickups").toAbsolutePath().normalize();
    }

    private Path imageDir(String code) {
        return storageDir.resolve(IMAGE_DIR).resolve(normalizeCode(code)).toAbsolutePath().normalize();
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private String safeFileName(String value) {
        String fileName = value == null ? "" : Path.of(value).getFileName().toString();
        if (fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return fileName;
    }

    private String extension(String originalName, String contentType) {
        String fileName = originalName == null ? "" : Path.of(originalName).getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            String ext = fileName.substring(dot).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "");
            if (!ext.isBlank() && ext.length() <= 12) {
                return ext;
            }
        }
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }

    private List<CoursePickupImageDto> normalizeImages(List<CoursePickupImageDto> images) {
        return images == null ? List.of() : images.stream()
                .filter(image -> image != null && image.fileName() != null && !image.fileName().isBlank())
                .toList();
    }

    private String now() {
        return OffsetDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private void deleteDirectory(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path item : paths) {
                Files.deleteIfExists(item);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to delete pickup directory: " + path, exception);
        }
    }
}
