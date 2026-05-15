package io.github.tissyboxc.clark_aams_backend.pickup;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/admin/course-pickups", produces = "application/json;charset=UTF-8")
public class CoursePickupAdminController {
    private final CoursePickupStore pickupStore;

    public CoursePickupAdminController(CoursePickupStore pickupStore) {
        this.pickupStore = pickupStore;
    }

    @GetMapping
    public ApiResponse<List<CoursePickupSummaryDto>> list() {
        return ApiResponse.ok(pickupStore.list());
    }

    @GetMapping("/{code}")
    public ApiResponse<CoursePickupRecordDto> get(@PathVariable String code) {
        return ApiResponse.ok(pickupStore.find(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "取件码不存在")));
    }

    @PutMapping("/{code}")
    public ApiResponse<CoursePickupRecordDto> updateJson(
            @PathVariable String code,
            @RequestBody CoursePickupUpdateRequest request
    ) {
        return ApiResponse.ok(pickupStore.updateJson(code, request == null ? null : request.jsonText()));
    }

    @DeleteMapping("/{code}")
    public ApiResponse<Void> delete(@PathVariable String code) {
        pickupStore.delete(code);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{code}/images/{fileName}")
    public ApiResponse<Void> deleteImage(@PathVariable String code, @PathVariable String fileName) {
        pickupStore.deleteImage(code, fileName);
        return ApiResponse.ok(null);
    }

    @GetMapping(value = "/{code}/images/{fileName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Resource> image(@PathVariable String code, @PathVariable String fileName) throws Exception {
        Path path = pickupStore.imagePath(code, fileName);
        String contentType = Files.probeContentType(path);
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(new FileSystemResource(path));
    }
}
