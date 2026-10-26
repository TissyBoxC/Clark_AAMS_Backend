package io.github.tissyboxc.clark_aams_backend.admin;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配额看板接口（P5-4）。
 *
 * <p>挂在 {@code /api/v1/admin} 下，因此自动受
 * {@link AdminAuthFilter} 保护——不需要在这里重复鉴权判断。
 *
 * <p>只读：看板不提供「重置配额」之类的写操作。
 * 需要清空时改配置重启即可，比开一个能远程清额度的接口安全得多。
 */
@RestController
@RequestMapping(value = "/api/v1/admin", produces = "application/json;charset=UTF-8")
public class QuotaDashboardController {

    private final QuotaDashboardService service;

    public QuotaDashboardController(QuotaDashboardService service) {
        this.service = service;
    }

    /** 今日用量快照。 */
    @GetMapping("/quota")
    public ApiResponse<QuotaDashboardDto> quota() {
        return ApiResponse.ok(service.snapshot());
    }
}
