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

    /**
     * 把存储的课表 JSON 文本解析为对象。
     *
     * <p>存储层刻意只保留原始文本（识别结果原样落盘，便于排障与重放），
     * 因此读取接口需要在这里解析一次，才能放进统一信封（P2-2）。
     */
    public Object parseCourses(String jsonText) {
        try {
            return objectMapper.readValue(jsonText, Object.class);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.COURSE_PARSE_FAILED, "课表 JSON 解析失败", exception);
        }
    }
}
