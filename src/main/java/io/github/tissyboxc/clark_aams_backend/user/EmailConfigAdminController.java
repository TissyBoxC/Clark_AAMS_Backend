package io.github.tissyboxc.clark_aams_backend.user;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/admin/email", produces = "application/json;charset=UTF-8")
public class EmailConfigAdminController {
    private final EmailConfigStore configStore;

    public EmailConfigAdminController(EmailConfigStore configStore) {
        this.configStore = configStore;
    }

    @GetMapping("/config")
    public ApiResponse<EmailConfigDto> getConfig() {
        return ApiResponse.ok(configStore.current());
    }

    @PutMapping("/config")
    public ApiResponse<EmailConfigDto> updateConfig(@RequestBody EmailConfigUpdateRequest request) {
        return ApiResponse.ok(configStore.save(request));
    }
}
