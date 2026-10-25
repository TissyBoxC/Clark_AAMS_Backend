package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.security.QuotaGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 敏感接口的登录与配额校验（审查条目 D1/D2/D6）。
 *
 * 验收要求：未登录 401；超限 429。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "clark-aams.client-version.config-path=${java.io.tmpdir}/clark-aams-auth-test-client-version.json",
        "clark-aams.pickup.storage-dir=${java.io.tmpdir}/clark-aams-auth-test-pickups",
        "clark-aams.import.unsupported-html-dir=${java.io.tmpdir}/clark-aams-auth-test-unsupported-html",
        "clark-aams.user.db-path=${java.io.tmpdir}/clark-aams-auth-test-users.db",
        "clark-aams.admin.username=test-admin",
        "clark-aams.admin.password=test-password",
        // 把配额压到很小，便于在测试里触发 429
        "clark-aams.quota.analyze.daily-limit=2",
        "clark-aams.quota.analyze.per-minute=100",
        "clark-aams.quota.recognize.daily-limit=1",
        "clark-aams.quota.recognize.per-minute=100"
})
class SensitiveEndpointAuthTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuotaGuard quotaGuard;

    @BeforeEach
    void resetQuota() {
        quotaGuard.reset();
    }

    @Test
    @DisplayName("未登录调用题目解析返回 401")
    void analyzeRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/questions/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"1+1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("未登录调用填空解析返回 401")
    void analyzeFillBlankRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/questions/analyze/fill-blank")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"填空\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("未登录创建取件码返回 401")
    void createPickupRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/course-pickups")
                        .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("未登录调用图片识别返回 401")
    void recognizeRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/courses/recognize")
                        .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("读取取件码保持匿名可访问（取件码本身即凭据）")
    void getPickupStaysAnonymous() throws Exception {
        // 用一个不存在的取件码，验证它走的是「不存在」而不是 401
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/course-pickups/NOT-EXIST"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("已登录但超出每日配额返回 429")
    void analyzeReturns429WhenQuotaExceeded() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("CLARK_AAMS_USER_ID", 424242L);

        // 每日上限 2：前两次可能因题面不合法而 400，但配额已经扣掉，
        // 因此第 3 次必须被限流拦下
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/questions/analyze")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"question\":\"1+1\"}"));
        }

        mockMvc.perform(post("/api/v1/questions/analyze")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"1+1\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42901));
    }

    @Test
    @DisplayName("达到每分钟上限返回 42902（限流先于配额）")
    void analyzeReturns42902WhenRateLimited() throws Exception {
        MockHttpSession session = new MockHttpSession();
        // 用一个专门的用户 id，避免与配额用例互相影响
        session.setAttribute("CLARK_AAMS_USER_ID", 434343L);

        // per-minute 配置为 100，先占满 100 次
        for (int i = 0; i < 100; i++) {
            quotaGuard.acquireRate("analyze", 434343L, 60_000L, 100);
        }

        mockMvc.perform(post("/api/v1/questions/analyze")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"1+1\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42902));
    }
}
