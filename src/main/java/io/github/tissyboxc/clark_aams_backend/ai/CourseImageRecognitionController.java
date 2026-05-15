package io.github.tissyboxc.clark_aams_backend.ai;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/courses")
public class CourseImageRecognitionController {
    private final CourseImageRecognitionService recognitionService;

    public CourseImageRecognitionController(CourseImageRecognitionService recognitionService) {
        this.recognitionService = recognitionService;
    }

    @PostMapping(
            value = "/recognize",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = "application/json;charset=UTF-8"
    )
    public ResponseEntity<String> recognize(@RequestParam("images") List<MultipartFile> images) {
        return ResponseEntity.ok()
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(recognitionService.recognize(images));
    }
}
