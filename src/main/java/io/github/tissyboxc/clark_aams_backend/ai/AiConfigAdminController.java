package io.github.tissyboxc.clark_aams_backend.ai;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/admin/ai", produces = "application/json;charset=UTF-8")
public class AiConfigAdminController {
    private final AiConfigStore configStore;

    public AiConfigAdminController(AiConfigStore configStore) {
        this.configStore = configStore;
    }

    @GetMapping("/config")
    public ApiResponse<AiConfigDto> getConfig() {
        return ApiResponse.ok(configStore.current());
    }

    @PutMapping("/config")
    public ApiResponse<AiConfigDto> updateConfig(@RequestBody AiConfigUpdateRequest request) {
        validate(request);
        return ApiResponse.ok(configStore.save(request));
    }

    private void validate(AiConfigUpdateRequest request) {
        if (request == null || blank(request.baseUrl()) || blank(request.model())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
