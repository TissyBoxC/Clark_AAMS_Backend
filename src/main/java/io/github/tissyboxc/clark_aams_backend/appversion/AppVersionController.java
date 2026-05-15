package io.github.tissyboxc.clark_aams_backend.appversion;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/app/version", produces = "application/json;charset=UTF-8")
public class AppVersionController {
    private final AppVersionService appVersionService;

    public AppVersionController(AppVersionService appVersionService) {
        this.appVersionService = appVersionService;
    }

    @GetMapping
    public ApiResponse<VersionCheckResponse> checkByQuery(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String currentVersion,
            @RequestParam(required = false) Integer currentBuild,
            @RequestParam(required = false) String channel
    ) {
        return ApiResponse.ok(appVersionService.check(new VersionCheckRequest(
                platform,
                currentVersion,
                currentBuild,
                channel,
                null
        )));
    }

    @PostMapping("/check")
    public ApiResponse<VersionCheckResponse> checkByBody(@RequestBody VersionCheckRequest request) {
        return ApiResponse.ok(appVersionService.check(request));
    }
}
