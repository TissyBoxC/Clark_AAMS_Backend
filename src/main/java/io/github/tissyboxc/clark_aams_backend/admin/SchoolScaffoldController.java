package io.github.tissyboxc.clark_aams_backend.admin;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/admin", produces = "application/json;charset=UTF-8")
public class SchoolScaffoldController {
    private final SchoolScaffoldService schoolScaffoldService;

    public SchoolScaffoldController(SchoolScaffoldService schoolScaffoldService) {
        this.schoolScaffoldService = schoolScaffoldService;
    }

    @PostMapping("/school-scaffold")
    public ApiResponse<SchoolScaffoldResponse> scaffoldSchool(@RequestBody SchoolScaffoldRequest request) {
        return ApiResponse.ok(schoolScaffoldService.scaffold(request));
    }
}
