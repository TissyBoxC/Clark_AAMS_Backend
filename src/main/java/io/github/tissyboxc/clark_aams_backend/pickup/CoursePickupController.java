package io.github.tissyboxc.clark_aams_backend.pickup;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/course-pickups")
public class CoursePickupController {
    private final CoursePickupService pickupService;
    private final CoursePickupStore pickupStore;

    public CoursePickupController(CoursePickupService pickupService, CoursePickupStore pickupStore) {
        this.pickupService = pickupService;
        this.pickupStore = pickupStore;
    }

    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = "application/json;charset=UTF-8"
    )
    public ApiResponse<CoursePickupCreateResponse> create(@RequestParam("images") List<MultipartFile> images) {
        return ApiResponse.ok(pickupService.recognizeAndCreate(images));
    }

    @GetMapping(value = "/{code}", produces = "application/json;charset=UTF-8")
    public ResponseEntity<String> get(@PathVariable String code) {
        CoursePickupRecordDto record = pickupStore.find(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "取件码不存在"));
        return ResponseEntity.ok()
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(record.jsonText());
    }
}
