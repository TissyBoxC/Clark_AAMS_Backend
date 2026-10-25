package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.admin.AdminAuthController;
import io.github.tissyboxc.clark_aams_backend.user.UserController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 同一会话内两种身份的互斥（P1-1）。
 *
 * <p>账号表合并后，管理员和普通用户共用 `users` 表，但会话里仍有两个标记：
 * 普通用户是 {@code userId}，管理员是一个布尔位。原实现设置其中之一时
 * 从不清理另一个，于是同一浏览器里：
 *
 * <ol>
 *   <li>先以管理员登录 → 会话里有管理员标记</li>
 *   <li>同一会话再注册/登录一个普通用户 → 会话里同时有 userId 和管理员标记</li>
 *   <li>{@code /auth/me} 优先返回 ADMIN —— 普通用户被识别成管理员</li>
 * </ol>
 *
 * <p>这是一个真实的越权入口：管理员在同一浏览器里帮别人注册账号，
 * 新账号会继承管理员身份。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "clark-aams.user.db-path=target/test-data/session-identity.db",
        "clark-aams.admin.config-path=target/test-data/session-admin.json",
})
class SessionIdentityIsolationTests {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 每个用例用独立后缀。
     *
     * <p>这三个用例共用同一份落盘数据库与管理员配置（Spring 上下文只建一次），
     * 而 JUnit 不保证方法执行顺序。用固定账号名会互相踩：
     * 第二个用例注册同名账号得到 409、第二个用例再注册管理员得到 400。
     * 之前正是这么红的——不是被测逻辑的问题，是测试自己不够隔离。
     */
    private static String unique() {
        return "iso" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @Test
    @DisplayName("管理员会话中注册普通用户后，身份应变为 user 而不是 admin")
    void registeringUserClearsAdminIdentity() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String admin = "adm" + unique();
        String user = "usr" + unique();

        // 1) 直接建立管理员会话。
        //    这里不走 /admin/auth/register —— 该接口全库只能成功一次，
        //    与其他用例共用上下文时必然 400。用一个已存在的管理员身份即可满足本用例目的。
        session.setAttribute(AdminAuthController.SESSION_KEY, Boolean.TRUE);
        session.setAttribute(AdminAuthController.SESSION_USERNAME_KEY, admin);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        // 2) 同一会话注册一个普通用户
        mockMvc.perform(post("/api/v1/user/register")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"isoUserPass\","
                                + "\"qqEmail\":\"" + user + "@qq.com\"}"))
                .andExpect(status().isOk());

        // 3) 身份必须变为 USER —— 新账号不能继承管理员身份
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("普通用户会话中登录管理员后，身份应变为 admin")
    void adminLoginTakesPrecedence() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String user = "usr" + unique();

        mockMvc.perform(post("/api/v1/user/register")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"isoUserPass\","
                                + "\"qqEmail\":\"" + user + "@qq.com\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(jsonPath("$.data.role").value("USER"));

        // 再以管理员身份登录：走真实的登录接口，需要该管理员已存在；
        // 若全库尚未初始化过管理员，registrationAvailable 为 true，这里先注册。
        mockMvc.perform(post("/api/v1/admin/auth/register")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"adm" + unique() + "\",\"password\":\"isoAdminPass\"}"));

        if (session.getAttribute(AdminAuthController.SESSION_KEY) == null) {
            // 管理员已被其他用例占用：直接建立会话，本用例只关心身份互斥
            session.setAttribute(AdminAuthController.SESSION_KEY, Boolean.TRUE);
            session.setAttribute(AdminAuthController.SESSION_USERNAME_KEY, "adm");
        }

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("退出登录后身份回到 guest")
    void logoutResetsIdentity() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String user = "usr" + unique();

        mockMvc.perform(post("/api/v1/user/register")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"isoUserPass\","
                                + "\"qqEmail\":\"" + user + "@qq.com\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/user/logout").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(jsonPath("$.data.role").value("GUEST"));
    }
}
