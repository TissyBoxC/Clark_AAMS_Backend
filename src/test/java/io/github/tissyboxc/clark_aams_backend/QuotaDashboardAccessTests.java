package io.github.tissyboxc.clark_aams_backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 配额看板接口的访问控制（P5-4）。
 *
 * <p>看板展示的是**全体用户的用量**：谁用了多少次、用了哪个业务域。
 * 这类数据本身就是隐私信息——能推断出某个人在用识别功能、用了多久。
 *
 * <p>所以它不能只是「挂个接口就完事」，必须确认：
 * <ul>
 *   <li>匿名访问被拒（401）</li>
 *   <li>普通用户访问被拒（403）——这是最容易漏的一条：
 *       一旦漏了，任何注册用户都能看到全部人的用量</li>
 *   <li>管理员能看到</li>
 * </ul>
 *
 * <p>留一个接口只在「管理员会话下」返回数据，比事后补测试便宜得多。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "clark-aams.client-version.config-path=${java.io.tmpdir}/clark-aams-quota-dashboard-client-version.json",
        "clark-aams.pickup.storage-dir=${java.io.tmpdir}/clark-aams-quota-dashboard-pickups",
        "clark-aams.import.unsupported-html-dir=${java.io.tmpdir}/clark-aams-quota-dashboard-html",
        "clark-aams.user.db-path=${java.io.tmpdir}/clark-aams-quota-dashboard-users.db",
        "clark-aams.admin.username=quota-admin",
        "clark-aams.admin.password=quota-password"
})
class QuotaDashboardAccessTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("匿名访问看板返回 401")
    void anonymousDenied() throws Exception {
        mockMvc.perform(get("/api/v1/admin/quota").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("普通用户访问看板返回 403")
    void regularUserDenied() throws Exception {
        // 先注册并登录一个普通用户
        String username = "quota-user-" + java.util.UUID.randomUUID();
        mockMvc.perform(post("/api/v1/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"quotaPass1234","qqEmail":"%s@qq.com"}
                                """.formatted(username, username)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/quota")
                        .session(userSession(username))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员可以看到看板")
    void adminAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/admin/quota")
                        .session(adminSession())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalCalls").exists())
                .andExpect(jsonPath("$.data.inMemoryOnly").value(true));
    }

    /** 用管理员凭据登录，拿到带会话的请求构造器。 */
    private org.springframework.mock.web.MockHttpSession adminSession() throws Exception {
        var result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"quota-admin","password":"quota-password"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return (org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false);
    }

    private org.springframework.mock.web.MockHttpSession userSession(String username) throws Exception {
        var result = mockMvc.perform(post("/api/v1/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"quotaPass1234"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return (org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false);
    }
}
