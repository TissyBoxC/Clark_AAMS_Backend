package io.github.tissyboxc.clark_aams_backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 角色越权隔离（审查条目 P1-3）。
 *
 * <p>验证 `/api/v1/admin/**` 只对管理员开放：
 * 匿名 401、普通用户 403、管理员 200。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "clark-aams.client-version.config-path=${java.io.tmpdir}/clark-aams-role-test-client-version.json",
        "clark-aams.pickup.storage-dir=${java.io.tmpdir}/clark-aams-role-test-pickups",
        "clark-aams.import.unsupported-html-dir=${java.io.tmpdir}/clark-aams-role-test-unsupported-html",
        "clark-aams.user.db-path=${java.io.tmpdir}/clark-aams-role-test-users.db",
        "clark-aams.admin.username=role-admin",
        "clark-aams.admin.password=role-password"
})
class RoleAccessTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("匿名访问后台接口返回 401")
    void anonymousGets401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ai/config"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("普通用户访问后台接口返回 403，而非 401")
    void regularUserGets403() throws Exception {
        MockHttpSession session = registerUser();

        mockMvc.perform(get("/api/v1/admin/ai/config").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    @DisplayName("普通用户不能借用户会话读后台取件码列表")
    void regularUserCannotReadAdminPickups() throws Exception {
        MockHttpSession session = registerUser();

        mockMvc.perform(get("/api/v1/admin/course-pickups").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员可访问后台接口")
    void adminGets200() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"role-admin\",\"password\":\"role-password\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/ai/config").session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("后台登录/初始化接口本身不需要管理员身份（否则永远进不去）")
    void adminAuthEndpointsAreOpen() throws Exception {
        mockMvc.perform(get("/api/v1/admin/auth/setup"))
                .andExpect(status().isOk());
    }

    /** 注册用户并返回已登录的会话。用户名带随机后缀避免测试库复用冲突。 */
    private MockHttpSession registerUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "roleuser_" + suffix;
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/v1/user/register")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"pass1234","qqEmail":"%s@qq.com"}
                                """.formatted(username, username)))
                .andExpect(status().isOk());
        return session;
    }
}
