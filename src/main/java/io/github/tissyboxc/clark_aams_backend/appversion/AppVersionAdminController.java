package io.github.tissyboxc.clark_aams_backend.appversion;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/admin/app-version", produces = "application/json;charset=UTF-8")
public class AppVersionAdminController {
    private final AppVersionConfigStore configStore;

    public AppVersionAdminController(AppVersionConfigStore configStore) {
        this.configStore = configStore;
    }

    @GetMapping("/config")
    public ApiResponse<AppVersionConfigDto> getConfig() {
        return ApiResponse.ok(configStore.current());
    }

    @PutMapping("/config")
    public ApiResponse<AppVersionConfigDto> updateConfig(@RequestBody AppVersionUpdateRequest request) {
        validate(request);
        return ApiResponse.ok(configStore.save(request));
    }

    private void validate(AppVersionUpdateRequest request) {
        if (request == null
                || normalize(request.latestVersion()).isBlank()
                || normalize(request.minimumSupportedVersion()).isBlank()
                || request.latestBuild() == null
                || request.minimumSupportedBuild() == null
                || request.latestBuild() < 0
                || request.minimumSupportedBuild() < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        SemanticVersion.parse(request.latestVersion());
        SemanticVersion.parse(request.minimumSupportedVersion());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
