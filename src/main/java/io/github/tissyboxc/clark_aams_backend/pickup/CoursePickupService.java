package io.github.tissyboxc.clark_aams_backend.pickup;

import io.github.tissyboxc.clark_aams_backend.ai.CourseImageRecognitionService;
import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class CoursePickupService {
    private final CourseImageRecognitionService recognitionService;
    private final CoursePickupStore pickupStore;
    private final ObjectMapper objectMapper;

    public CoursePickupService(
            CourseImageRecognitionService recognitionService,
            CoursePickupStore pickupStore,
            ObjectMapper objectMapper
    ) {
        this.recognitionService = recognitionService;
        this.pickupStore = pickupStore;
        this.objectMapper = objectMapper;
    }

    public CoursePickupCreateResponse recognizeAndCreate(List<MultipartFile> images) {
        String jsonText = recognitionService.recognize(images);
        CoursePickupRecordDto record = pickupStore.create(jsonText, images);
        return new CoursePickupCreateResponse(record.code(), parseCourses(jsonText));
    }

    private Object parseCourses(String jsonText) {
        try {
            return objectMapper.readValue(jsonText, Object.class);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.COURSE_PARSE_FAILED, "课表 JSON 解析失败", exception);
        }
    }
}
