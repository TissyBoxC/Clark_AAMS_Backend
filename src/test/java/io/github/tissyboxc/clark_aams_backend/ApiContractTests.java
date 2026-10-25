package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.ai.QuestionAnalysisService;
import io.github.tissyboxc.clark_aams_backend.pickup.CoursePickupRecordDto;
import io.github.tissyboxc.clark_aams_backend.pickup.CoursePickupStore;
import io.github.tissyboxc.clark_aams_backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "clark-aams.client-version.config-path=${java.io.tmpdir}/clark-aams-backend-test-client-version.json",
        "clark-aams.pickup.storage-dir=${java.io.tmpdir}/clark-aams-backend-test-pickups",
        "clark-aams.import.unsupported-html-dir=${java.io.tmpdir}/clark-aams-backend-test-unsupported-html",
        "clark-aams.user.db-path=${java.io.tmpdir}/clark-aams-backend-test-users.db",
        "clark-aams.admin.username=test-admin",
        "clark-aams.admin.password=test-password"
})
class ApiContractTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CoursePickupStore pickupStore;

    @Autowired
    private UserRepository userRepository;

    @Test
    void listSchoolsReturnsBuiltInJitSchool() throws Exception {
        mockMvc.perform(get("/api/v1/schools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value("jit"))
                .andExpect(jsonPath("$.data[0].name").value("\u91d1\u9675\u79d1\u6280\u5b66\u9662"))
                .andExpect(jsonPath("$.data[0].lessonTimeProfile").value("general-a"))
                .andExpect(jsonPath("$.data[0].capabilities.academicImport").value(true));
    }

    @Test
    void schoolLessonTimesReturnsThirteenLessons() throws Exception {
        mockMvc.perform(get("/api/v1/schools/jit/lesson-times"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.schoolId").value("jit"))
                .andExpect(jsonPath("$.data.profile").value("general-a"))
                .andExpect(jsonPath("$.data.lessons", hasSize(13)))
                .andExpect(jsonPath("$.data.lessons[0].lesson").value(1))
                .andExpect(jsonPath("$.data.lessons[0].startTime").value("08:30"))
                .andExpect(jsonPath("$.data.lessons[0].endTime").doesNotExist())
                .andExpect(jsonPath("$.data.lessons[7].startTime").value("16:15"))
                .andExpect(jsonPath("$.data.lessons[12].lesson").value(13));
    }

    @Test
    void lessonTimeProfilesReturnGeneralProfilesWithoutSchool() throws Exception {
        mockMvc.perform(get("/api/v1/schools/lesson-time-profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].profile").value("general-a"))
                .andExpect(jsonPath("$.data[0].lessons", hasSize(13)))
                .andExpect(jsonPath("$.data[0].lessons[0].startTime").value("08:30"))
                .andExpect(jsonPath("$.data[1].profile").value("general-b"))
                .andExpect(jsonPath("$.data[1].lessons[0].startTime").value("08:00"));
    }

    @Test
    void healthReturnsUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void questionAnalysisRequiresUserLogin() throws Exception {
        mockMvc.perform(post("/api/v1/questions/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question_type": "single_choice",
                                  "question_content": "Pick one",
                                  "options": ["A. one", "B. two"]
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void questionAnalysisRejectsMissingQuestionContent() throws Exception {
        MockHttpSession session = registerAsUser();

        mockMvc.perform(post("/api/v1/questions/analyze")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question_type": "single_choice",
                                  "options": ["A. one", "B. two"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void fillBlankQuestionAnalysisAllowsMissingOptions() throws Exception {
        MockHttpSession session = registerAsUser();

        mockMvc.perform(post("/api/v1/questions/analyze/fill-blank")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question_type": "essay",
                                  "question_content": "Explain the purpose of an interface.",
                                  "material": "Use Java as the context."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.answer").value("fill answer"));
    }

    @Test
    void questionAnalysisRestoresSessionFromLoginCodeHeader() throws Exception {
        RegisteredUser user = registerUser();

        MvcResult result = mockMvc.perform(post("/api/v1/questions/analyze")
                        .header("X-Clark-Aams-Login-Code", user.loginCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question_type": "single_choice",
                                  "question_content": "Pick one",
                                  "options": ["A. one", "B. two"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.answer").value("A"))
                .andReturn();

        MockHttpSession restoredSession = (MockHttpSession) result.getRequest().getSession(false);
        assertTrue(restoredSession != null);

        mockMvc.perform(get("/api/v1/user/me").session(restoredSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.email").value(user.email()))
                .andExpect(jsonPath("$.data.questionAnalysisCount").value(1));
    }

    @Test
    void userLoginSupportsEmailAndLoginCode() throws Exception {
        RegisteredUser user = registerUser();

        mockMvc.perform(post("/api/v1/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "qqEmail": "%s",
                                  "password": "%s"
                                }
                                """.formatted(user.email(), user.password())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.email").value(user.email()))
                .andExpect(jsonPath("$.data.questionAnalysisCount").value(0));

        mockMvc.perform(post("/api/v1/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginCode": "%s"
                                }
                                """.formatted(user.loginCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.email").value(user.email()))
                .andExpect(jsonPath("$.data.questionAnalysisCount").value(0));
    }

    @Test
    void emailVerificationSupportsLoginCodeRestoreAndUpdatesMe() throws Exception {
        RegisteredUser user = registerUser();

        mockMvc.perform(post("/api/v1/user/email/code")
                        .header("X-Clark-Aams-Login-Code", user.loginCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "qqEmail": "%s"
                                }
                                """.formatted(user.email())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.emailVerified").value(false));

        String verificationCode = userRepository.findByLoginCode(user.loginCode())
                .orElseThrow()
                .emailVerifyCode();

        MvcResult verifyResult = mockMvc.perform(post("/api/v1/user/email/verify")
                        .header("X-Clark-Aams-Login-Code", user.loginCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s"
                                }
                                """.formatted(verificationCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.emailBound").value(true))
                .andExpect(jsonPath("$.data.emailVerified").value(true))
                .andReturn();

        MockHttpSession restoredSession = (MockHttpSession) verifyResult.getRequest().getSession(false);
        assertTrue(restoredSession != null);

        mockMvc.perform(get("/api/v1/user/me").session(restoredSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.email").value(user.email()))
                .andExpect(jsonPath("$.data.emailBound").value(true))
                .andExpect(jsonPath("$.data.emailVerified").value(true));
    }

    @Test
    void emailCodeRequestHasPerUserCooldown() throws Exception {
        RegisteredUser user = registerUser();

        mockMvc.perform(post("/api/v1/user/email/code")
                        .header("X-Clark-Aams-Login-Code", user.loginCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "qqEmail": "%s"
                                }
                                """.formatted(user.email())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/user/email/code")
                        .header("X-Clark-Aams-Login-Code", user.loginCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "qqEmail": "%s"
                                }
                                """.formatted(user.email())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("验证码发送过于频繁，请 ")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.endsWith(" 秒后再试")));
    }

    @Test
    void adminLoginRejectsInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "test-admin",
                                  "password": "wrong"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void adminApiRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/app-version/config"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void adminApiUnauthorizedResponseIncludesCorsHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/admin/app-version/config")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("请先登录后台管理"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void adminApiPreflightRequestIsNotBlockedByAuthFilter() throws Exception {
        mockMvc.perform(options("/api/v1/admin/app-version/config")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "x-requested-with"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void sessionCookieFilterAddsCrossSiteCookieAttributesForHttpsRequests() throws Exception {
        SessionCookieFilter filter = new SessionCookieFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/auth/login");
        request.setServerName("api.clarkhub.cn");
        request.setServerPort(80);
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                ((jakarta.servlet.http.HttpServletResponse) res)
                        .addCookie(createSessionCookie()));

        org.junit.jupiter.api.Assertions.assertTrue(response.getHeader("Set-Cookie").contains("JSESSIONID=test-session-id"));
        org.junit.jupiter.api.Assertions.assertTrue(response.getHeader("Set-Cookie").contains("SameSite=None"));
        org.junit.jupiter.api.Assertions.assertTrue(response.getHeader("Set-Cookie").contains("Secure"));
        org.junit.jupiter.api.Assertions.assertTrue(response.getHeader("Set-Cookie").contains("HttpOnly"));
    }

    @Test
    void adminVersionConfigCanBeUpdated() throws Exception {
        MockHttpSession session = loginAsAdmin();
        String original = mockMvc.perform(get("/api/v1/admin/app-version/config").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String originalPayload = objectMapper.writeValueAsString(
                objectMapper.readTree(original).get("data")
        );

        try {
            mockMvc.perform(put("/api/v1/admin/app-version/config")
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "latestVersion": "1.2.0",
                                      "latestBuild": 3,
                                      "minimumSupportedVersion": "1.0.0",
                                      "minimumSupportedBuild": 1,
                                      "title": "Update Found",
                                      "optionalUpdateMessage": "Optional update available",
                                      "requiredUpdateMessage": "Please update before continuing",
                                      "releasePageUrl": "https://example.com/releases",
                                      "releaseNotes": ["Added admin version config"],
                                      "downloadSources": [
                                        {
                                          "type": "gitee",
                                          "label": "Gitee Download",
                                          "url": "https://example.com/download",
                                          "primary": true,
                                          "description": "Primary mirror"
                                        }
                                      ]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.latestVersion").value("1.2.0"))
                    .andExpect(jsonPath("$.data.latestBuild").value(3))
                    .andExpect(jsonPath("$.data.downloadSources", hasSize(1)));
            String persisted = Files.readString(Path.of(System.getProperty("java.io.tmpdir"), "clark-aams-backend-test-client-version.json"));
            assertTrue(persisted.contains("\"latestVersion\" : \"1.2.0\""));
        } finally {
            mockMvc.perform(put("/api/v1/admin/app-version/config")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(originalPayload));
        }
    }

    @Test
    void adminVersionConfigCanBePatched() throws Exception {
        MockHttpSession session = loginAsAdmin();
        String original = mockMvc.perform(get("/api/v1/admin/app-version/config").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String originalPayload = objectMapper.writeValueAsString(
                objectMapper.readTree(original).get("data")
        );
        String originalMinimumSupportedVersion = objectMapper.readTree(original)
                .get("data")
                .get("minimumSupportedVersion")
                .asText();
        int originalLatestBuild = objectMapper.readTree(original)
                .get("data")
                .get("latestBuild")
                .asInt();

        try {
            mockMvc.perform(patch("/api/v1/admin/app-version/config")
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "latestVersion": "1.2.1"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.latestVersion").value("1.2.1"))
                    .andExpect(jsonPath("$.data.latestBuild").value(originalLatestBuild))
                    .andExpect(jsonPath("$.data.minimumSupportedVersion").value(originalMinimumSupportedVersion));

            String persisted = Files.readString(Path.of(System.getProperty("java.io.tmpdir"), "clark-aams-backend-test-client-version.json"));
            assertTrue(persisted.contains("\"latestVersion\" : \"1.2.1\""));
            assertTrue(persisted.contains("\"minimumSupportedVersion\" : \"" + originalMinimumSupportedVersion + "\""));
        } finally {
            mockMvc.perform(put("/api/v1/admin/app-version/config")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(originalPayload));
        }
    }

    @Test
    void importCoursesParsesJitTable6Html() throws Exception {
        String html = """
                <html>
                  <body id="index-charts">
                    <table id="Table6">
                      <tbody>
                        <tr>
                          <td>\u661f\u671f\u4e00</td>
                          <td>
                            <p class="kblsbk">
                              <span class="time">1-2\u8282(1-4\u5468)</span>
                              <span class="kejie">\u9ad8\u7b49\u6570\u5b66</span>
                              <span class="didian">A101</span>
                              <span class="teacher">\u5f20\u4e09</span>
                            </p>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </body>
                </html>
                """;
        String request = """
                {
                  "loginSession": {
                    "extra": {
                      "html": %s
                    }
                  }
                }
                """.formatted(objectMapper.writeValueAsString(html));

        mockMvc.perform(post("/api/v1/imports/jit/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.schoolId").value("jit"))
                .andExpect(jsonPath("$.data.courses", hasSize(1)))
                .andExpect(jsonPath("$.data.courses[0].name").value("\u9ad8\u7b49\u6570\u5b66"))
                .andExpect(jsonPath("$.data.courses[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.data.courses[0].startLesson").value(1))
                .andExpect(jsonPath("$.data.courses[0].endLesson").value(2))
                .andExpect(jsonPath("$.data.courses[0].location").value("A101"))
                .andExpect(jsonPath("$.data.courses[0].teacher").value("\u5f20\u4e09"))
                .andExpect(jsonPath("$.data.courses[0].weeks", hasSize(4)));
    }

    @Test
    void importCoursesParsesJluWutTableHtml() throws Exception {
        String html = """
                <table class="wut_table">
                  <tbody>
                    <tr>
                      <td>section</td>
                      <td data-week="1">
                        <div class="mtt_arrange_item">
                          <div class="mtt_item_kcmc">Advanced Math[01]</div>
                          <div class="mtt_item_jxbmc">Teacher Zhang</div>
                          <div class="mtt_item_room">1-6\u5468,\u661f\u671f1,\u7b2c3\u8282-\u7b2c4\u8282,Campus-323</div>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
                """;
        String request = """
                {
                  "loginSession": {
                    "extra": {
                      "html": %s
                    }
                  }
                }
                """.formatted(objectMapper.writeValueAsString(html));

        mockMvc.perform(post("/api/v1/imports/jlu/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.schoolId").value("jlu"))
                .andExpect(jsonPath("$.data.courses", hasSize(1)))
                .andExpect(jsonPath("$.data.courses[0].name").value("Advanced Math"))
                .andExpect(jsonPath("$.data.courses[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.data.courses[0].startLesson").value(3))
                .andExpect(jsonPath("$.data.courses[0].endLesson").value(4))
                .andExpect(jsonPath("$.data.courses[0].location").value("Campus-323"))
                .andExpect(jsonPath("$.data.courses[0].teacher").value("Teacher Zhang"))
                .andExpect(jsonPath("$.data.courses[0].weeks", hasSize(6)));
    }

    @Test
    void importCoursesPickupReturnsCodeForJluHtml() throws Exception {
        String html = """
                <table class="wut_table">
                  <tbody>
                    <tr>
                      <td>section</td>
                      <td data-week="2">
                        <div class="mtt_arrange_item">
                          <div class="mtt_item_kcmc">Linear Algebra[02]</div>
                          <div class="mtt_item_jxbmc">Teacher Li</div>
                          <div class="mtt_item_room">7-8\u5468,\u661f\u671f2,\u7b2c1\u8282-\u7b2c2\u8282,Campus-101</div>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
                """;
        String request = """
                {
                  "loginSession": {
                    "extra": {
                      "html": %s
                    }
                  }
                }
                """.formatted(objectMapper.writeValueAsString(html));

        mockMvc.perform(post("/api/v1/imports/jlu/courses/pickup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pickupCode").isString())
                .andExpect(jsonPath("$.data.courses", hasSize(1)))
                .andExpect(jsonPath("$.data.courses[0].name").value("Linear Algebra"));
    }

    @Test
    void importCoursesParsesJluCourseNameBeforeBracket() throws Exception {
        String html = """
                <table class="wut_table">
                  <tbody>
                    <tr>
                      <td>section</td>
                      <td data-week="4">
                        <div class="mtt_arrange_item">
                          <div class="mtt_item_kcmc">\u5de5\u7a0b\u6750\u6599[04] \u6731\u6c38\u798f 2-8\u5468,\u661f\u671f4,\u7b2c3\u8282-\u7b2c4\u8282,\u5357\u5cad-\u9038\u592b\u697c-A411 24\u8f66\u8f8601</div>
                          <div class="mtt_item_jxbmc">\u6731\u6c38\u798f</div>
                          <div class="mtt_item_room">2-8\u5468,\u661f\u671f4,\u7b2c3\u8282-\u7b2c4\u8282,\u5357\u5cad-\u9038\u592b\u697c-A411</div>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
                """;
        String request = """
                {
                  "loginSession": {
                    "extra": {
                      "html": %s
                    }
                  }
                }
                """.formatted(objectMapper.writeValueAsString(html));

        mockMvc.perform(post("/api/v1/imports/jlu/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.courses[0].name").value("\u5de5\u7a0b\u6750\u6599"));
    }

    @Test
    void importCoursesByHtmlParsesKnownSchool() throws Exception {
        String html = """
                <table class="wut_table">
                  <tbody>
                    <tr>
                      <td>section</td>
                      <td data-week="1">
                        <div class="mtt_arrange_item">
                          <div class="mtt_item_kcmc">Advanced Math[01]</div>
                          <div class="mtt_item_jxbmc">Teacher Zhang</div>
                          <div class="mtt_item_room">1-6\u5468,\u661f\u671f1,\u7b2c3\u8282-\u7b2c4\u8282,Campus-323</div>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
                """;

        mockMvc.perform(post("/api/v1/imports/courses/html")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "school": "jlu",
                                  "html": %s
                                }
                                """.formatted(objectMapper.writeValueAsString(html))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.status").value("parsed"))
                .andExpect(jsonPath("$.data.schoolId").value("jlu"))
                .andExpect(jsonPath("$.data.schoolName").value("\u5409\u6797\u5927\u5b66"))
                .andExpect(jsonPath("$.data.courses", hasSize(1)))
                .andExpect(jsonPath("$.data.courses[0].name").value("Advanced Math"));
    }

    @Test
    void importCoursesByHtmlSavesUnknownSchoolWhenNoAdapterMatches() throws Exception {
        String schoolName = "custom-school-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/imports/courses/html")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schoolName": "%s",
                                  "html": "<html><body>sample</body></html>"
                                }
                                """.formatted(schoolName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("\u4fdd\u5b58\u6210\u529f,\u8bf7\u7b49\u5f85\u6dfb\u52a0\u9002\u914d"))
                .andExpect(jsonPath("$.data.status").value("saved"))
                .andExpect(jsonPath("$.data.schoolName").value(schoolName))
                .andExpect(jsonPath("$.data.savedFileName").value(schoolName + ".html"));

        assertTrue(Files.exists(Path.of(
                System.getProperty("java.io.tmpdir"),
                "clark-aams-backend-test-unsupported-html",
                schoolName + ".html"
        )));
    }

    @Test
    void versionCheckReturnsOptionalUpdateWhenCurrentBuildIsBehindLatest() throws Exception {
        mockMvc.perform(get("/api/v1/app/version")
                        .param("platform", "android")
                        .param("currentVersion", "1.0.0")
                        .param("currentBuild", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.updateType").value("optional"))
                .andExpect(jsonPath("$.data.updateAvailable").value(true))
                .andExpect(jsonPath("$.data.forceUpdate").value(false));
    }

    @Test
    void versionCheckReturnsNoneWhenCurrentBuildIsLatest() throws Exception {
        mockMvc.perform(get("/api/v1/app/version")
                        .param("platform", "android")
                        .param("currentVersion", "1.1.0")
                        .param("currentBuild", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.updateType").value("none"))
                .andExpect(jsonPath("$.data.updateAvailable").value(false))
                .andExpect(jsonPath("$.data.forceUpdate").value(false));
    }

    @Test
    void versionCheckReturnsRequiredUpdateWhenCurrentBuildIsTooLow() throws Exception {
        mockMvc.perform(post("/api/v1/app/version/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "platform": "android",
                                  "currentVersion": "0.9.0",
                                  "currentBuild": 0,
                                  "channel": "stable"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.updateType").value("required"))
                .andExpect(jsonPath("$.data.updateAvailable").value(true))
                .andExpect(jsonPath("$.data.forceUpdate").value(true));
    }

    @Test
    void versionCheckStillRequiresUpdateWhenVersionIsBelowMinimumEvenIfBuildIsHigh() throws Exception {
        mockMvc.perform(get("/api/v1/app/version")
                        .param("platform", "android")
                        .param("currentVersion", "0.9.0")
                        .param("currentBuild", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.updateType").value("required"))
                .andExpect(jsonPath("$.data.forceUpdate").value(true));
    }

    @Test
    void pickupCodeReturnsStoredCourseJson() throws Exception {
        CoursePickupRecordDto record = pickupStore.create("""
                [
                  {
                    "name": "test-course",
                    "teacher": "test-teacher",
                    "position": "A101",
                    "day": 1,
                    "startSection": 1,
                    "endSection": 2,
                    "weeks": [1, 2],
                    "rawTime": "1-2 weeks, Monday"
                  }
                ]
                """, List.of());

        mockMvc.perform(get("/api/v1/course-pickups/{code}", record.code()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("test-course"))
                .andExpect(jsonPath("$[0].weeks", hasSize(2)));
    }

    private MockHttpSession loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "test-admin",
                                  "password": "test-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession registerAsUser() throws Exception {
        return registerUser().session();
    }

    private RegisteredUser registerUser() throws Exception {
        String email = "test-" + UUID.randomUUID() + "@qq.com";
        String password = "test-password";
        MvcResult result = mockMvc.perform(post("/api/v1/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "test-user",
                                  "password": "%s",
                                  "qqEmail": "%s"
                                }
                                """.formatted(password, email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.questionAnalysisCount").value(0))
                .andReturn();
        String loginCode = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data")
                .get("loginCode")
                .asText();
        return new RegisteredUser(email, password, loginCode, (MockHttpSession) result.getRequest().getSession(false));
    }

    private record RegisteredUser(String email, String password, String loginCode, MockHttpSession session) {
    }

    private jakarta.servlet.http.Cookie createSessionCookie() {
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("JSESSIONID", "test-session-id");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        return cookie;
    }

    @TestConfiguration
    static class QuestionAnalysisTestConfig {
        @Bean
        @Primary
        QuestionAnalysisService questionAnalysisService(
                io.github.tissyboxc.clark_aams_backend.ai.AiConfigStore configStore,
                ObjectMapper objectMapper
        ) {
            return new QuestionAnalysisService(configStore, objectMapper) {
                @Override
                public Map<String, Object> analyze(Map<String, Object> request) {
                    Object questionContent = request.get("question_content");
                    Object options = request.get("options");
                    if (questionContent == null || String.valueOf(questionContent).isBlank()) {
                        throw new io.github.tissyboxc.clark_aams_backend.common.BusinessException(
                                io.github.tissyboxc.clark_aams_backend.common.ErrorCode.BAD_REQUEST,
                                "question_content is required"
                        );
                    }
                    if (options == null) {
                        throw new io.github.tissyboxc.clark_aams_backend.common.BusinessException(
                                io.github.tissyboxc.clark_aams_backend.common.ErrorCode.BAD_REQUEST,
                                "options is required"
                        );
                    }
                    return Map.of("answer", "A");
                }

                @Override
                public Map<String, Object> analyzeFillBlank(Map<String, Object> request) {
                    Object questionContent = request.get("question_content");
                    if (questionContent == null || String.valueOf(questionContent).isBlank()) {
                        throw new io.github.tissyboxc.clark_aams_backend.common.BusinessException(
                                io.github.tissyboxc.clark_aams_backend.common.ErrorCode.BAD_REQUEST,
                                "question_content is required"
                        );
                    }
                    return Map.of("answer", "fill answer");
                }
            };
        }
    }
}
