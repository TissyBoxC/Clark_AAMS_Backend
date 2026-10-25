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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统一身份查询（`GET /api/v1/auth/me`）。
 *
 * <p>这是「登录后按身份进入不同工作台」的判据来源（审查文档 P1）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "clark-aams.client-version.config-path=${java.io.tmpdir}/clark-aams-identity-test-client-version.json",
        "clark-aams.pickup.storage-dir=${java.io.tmpdir}/clark-aams-identity-test-pickups",
        "clark-aams.import.unsupported-html-dir=${java.io.tmpdir}/clark-aams-identity-test-unsupported-html",
        "clark-aams.user.db-path=${java.io.tmpdir}/clark-aams-identity-test-users.db",
        "clark-aams.admin.username=identity-admin",
        "clark-aams.admin.password=identity-password"
})
class AuthIdentityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("未登录返回 role=guest")
    void guestIdentity() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("GUEST"))
                .andExpect(jsonPath("$.data.authenticated").value(false))
                .andExpect(jsonPath("$.data.user").doesNotExist());
    }

    @Test
    @DisplayName("用户会话返回 role=USER，且不含登录码")
    void userIdentity() throws Exception {
        // 注册一个真实用户，拿到会话
        MvcResultHolder holder = registerUser("iduser1");

        mockMvc.perform(get("/api/v1/auth/me").session(holder.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.user.loginCode").value(""))
                .andExpect(jsonPath("$.data.user.username").value(org.hamcrest.Matchers.startsWith("iduser1_")));
    }

    @Test
    @DisplayName("管理员会话返回 role=ADMIN")
    void adminIdentity() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"identity-admin\",\"password\":\"identity-password\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.displayName").value("identity-admin"));
    }

    @Test
    @DisplayName("管理员身份优先于用户身份（同一浏览器两种会话共存）")
    void adminTakesPrecedence() throws Exception {
        MvcResultHolder holder = registerUser("iduser2");
        MockHttpSession session = holder.session();

        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"identity-admin\",\"password\":\"identity-password\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("管理员登出后身份回到 guest（不残留用户身份）")
    void adminLogoutClearsIdentity() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"identity-admin\",\"password\":\"identity-password\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/auth/logout").session(session))
                .andExpect(status().isOk());

        // 登出会 invalidate 整个 session，新请求即匿名
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(jsonPath("$.data.role").value("GUEST"));
    }

    @Test
    @DisplayName("未登录访问 /user/me 返回 401，而不是 200 + 空用户")
    void userMeRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/user/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("登录后 /user/me 正常返回，且不含登录码")
    void userMeWorksWhenLoggedIn() throws Exception {
        MvcResultHolder holder = registerUser("meok");
        mockMvc.perform(get("/api/v1/user/me").session(holder.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginCode").value(""))
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    private record MvcResultHolder(MockHttpSession session) {
    }

    /**
     * 注册一个用户并返回其会话。
     *
     * <p>用户名与邮箱都带随机后缀：测试库文件在多次 `gradle test`
     * 之间是复用的，固定用户名会在第二次运行时撞 409 USER_EMAIL_CONFLICT。
     */
    private MvcResultHolder registerUser(String usernamePrefix) throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String username = usernamePrefix + "_" + suffix;
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/v1/user/register")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"pass1234","qqEmail":"%s@qq.com"}
                                """.formatted(username, username)))
                .andExpect(status().isOk());
        return new MvcResultHolder(session);
    }
}
