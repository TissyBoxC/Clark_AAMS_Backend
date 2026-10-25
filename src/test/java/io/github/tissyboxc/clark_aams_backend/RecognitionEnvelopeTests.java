package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.ai.CourseImageRecognitionController;
import io.github.tissyboxc.clark_aams_backend.ai.CourseImageRecognitionService;
import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import io.github.tissyboxc.clark_aams_backend.pickup.CoursePickupService;
import io.github.tissyboxc.clark_aams_backend.security.CurrentUserResolver;
import io.github.tissyboxc.clark_aams_backend.security.QuotaGuard;
import io.github.tissyboxc.clark_aams_backend.user.UserAccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 识别接口的信封契约（P2-2）。
 *
 * <p>真实接口会调用视觉大模型，不适合在单测里打真实网络，
 * 因此这里用 mock 的识别服务直接驱动控制器，
 * 验证「返回信封 / 保留配额头 / legacy 通道返回裸数组」三点。
 */
class RecognitionEnvelopeTests {

    private static final String COURSES_JSON = """
            [{ "name": "高等数学", "day": 1, "startSection": 1, "endSection": 2, "weeks": [1,2,3] }]
            """;

    private CourseImageRecognitionController controller(String quotaRemainingHeaderEnabled) {
        CourseImageRecognitionService recognitionService = mock(CourseImageRecognitionService.class);
        when(recognitionService.recognize(any())).thenReturn(COURSES_JSON);

        UserAccountService userAccountService = mock(UserAccountService.class);
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        when(resolver.requireUserId(any())).thenReturn(1L);

        QuotaGuard quotaGuard = new QuotaGuard();

        return new CourseImageRecognitionController(
                recognitionService,
                new CoursePickupService(
                        recognitionService,
                        mock(io.github.tissyboxc.clark_aams_backend.pickup.CoursePickupStore.class),
                        new tools.jackson.databind.ObjectMapper()
                ),
                resolver,
                quotaGuard,
                10,
                5
        );
    }

    private List<MultipartFile> oneImage() {
        return List.of(new MockMultipartFile("images", "a.png", "image/png", new byte[]{1, 2, 3}));
    }

    @Test
    @DisplayName("识别接口返回统一信封，data 为课程数组")
    void recognizeReturnsEnvelope() {
        CourseImageRecognitionController controller = controller("");
        ResponseEntity<ApiResponse<Object>> response = controller.recognize(oneImage(), new MockHttpServletRequest());

        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().code());
        Object data = response.getBody().data();
        assertInstanceOf(List.class, data, "data 应为课程数组");
        assertEquals(1, ((List<?>) data).size());
    }

    @Test
    @DisplayName("识别接口保留 X-Quota-Remaining 配额提示头")
    void recognizeKeepsQuotaHeader() {
        CourseImageRecognitionController controller = controller("");
        ResponseEntity<ApiResponse<Object>> response = controller.recognize(oneImage(), new MockHttpServletRequest());

        String remaining = response.getHeaders().getFirst("X-Quota-Remaining");
        assertNotNull(remaining, "配额头应保留，前端用它提示剩余次数");
        assertTrue(Integer.parseInt(remaining) >= 0);
    }

    @Test
    @DisplayName("legacy 通道返回裸数组（供未升级客户端）")
    void legacyChannelReturnsRawArray() {
        CourseImageRecognitionController controller = controller("");
        ResponseEntity<String> response = controller.recognizeLegacy(oneImage(), new MockHttpServletRequest());

        assertNotNull(response.getBody());
        String body = response.getBody().trim();
        assertTrue(body.startsWith("["), "legacy 通道应为裸数组，实际: " + body);
        assertEquals("true", response.getHeaders().getFirst("Deprecation"));
    }
}
