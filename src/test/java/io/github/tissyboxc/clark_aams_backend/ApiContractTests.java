package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.ai.QuestionAnalysisService;
import io.github.tissyboxc.clark_aams_backend.pickup.CoursePickupRecordDto;
import io.github.tissyboxc.clark_aams_backend.pickup.CoursePickupStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "clark-aams.client-version.config-path=${java.io.tmpdir}/clark-aams-backend-test-client-version.json",
        "clark-aams.pickup.storage-dir=${java.io.tmpdir}/clark-aams-backend-test-pickups",
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

    @Test
    void listSchoolsReturnsBuiltInJitSchool() throws Exception {
        mockMvc.perform(get("/api/v1/schools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value("jit"))
                .andExpect(jsonPath("$.data[0].name").value("金陵科技学院"))
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
    void faviconIsServedAsStaticResource() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isOk());
    }

    @Test
    void serviceWorkerCleanupIsServedAsStaticResource() throws Exception {
        mockMvc.perform(get("/sw.js"))
                .andExpect(status().isOk());
    }

    @Test
    void userEntryRedirectsToUserPage() throws Exception {
        mockMvc.perform(get("/user"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/index.html"));
    }

    @Test
    void adminEntryRedirectsToLoginWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login.html?redirect=%2Fadmin"));
    }

    @Test
    void adminLoginAllowsDashboardAccess() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/admin").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/index.html"));

        mockMvc.perform(get("/admin/index.html").session(session))
                .andExpect(status().isOk());
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
                                      "title": "发现新版本",
                                      "optionalUpdateMessage": "建议更新后继续使用。",
                                      "requiredUpdateMessage": "请更新后继续使用。",
                                      "releasePageUrl": "https://example.com/releases",
                                      "releaseNotes": ["新增版本管理后台"],
                                      "downloadSources": [
                                        {
                                          "type": "gitee",
                                          "label": "Gitee 下载",
                                          "url": "https://example.com/download",
                                          "primary": true,
                                          "description": "主下载源"
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
    void importCoursesParsesJitTable6Html() throws Exception {
        String html = """
                <html>
                  <body id="index-charts">
                    <table id="Table6">
                      <tbody>
                        <tr>
                          <td>星期一</td>
                          <td>
                            <p class="kblsbk">
                              <span class="time">1-2节 (1-4周)</span>
                              <span class="kejie">高等数学</span>
                              <span class="didian">A101</span>
                              <span class="teacher">张三</span>
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
                .andExpect(jsonPath("$.data.courses[0].name").value("高等数学"))
                .andExpect(jsonPath("$.data.courses[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.data.courses[0].startLesson").value(1))
                .andExpect(jsonPath("$.data.courses[0].endLesson").value(2))
                .andExpect(jsonPath("$.data.courses[0].location").value("A101"))
                .andExpect(jsonPath("$.data.courses[0].teacher").value("张三"))
                .andExpect(jsonPath("$.data.courses[0].weeks", hasSize(4)));
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
                    "name": "测试课程",
                    "teacher": "测试教师",
                    "position": "A101",
                    "day": 1,
                    "startSection": 1,
                    "endSection": 2,
                    "weeks": [1, 2],
                    "rawTime": "1-2周,星期1,第1节-第2节"
                  }
                ]
                """, List.of());

        mockMvc.perform(get("/api/v1/course-pickups/{code}", record.code()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("测试课程"))
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

    @TestConfiguration
    static class QuestionAnalysisTestConfig {
        @Bean
        @Primary
        QuestionAnalysisService questionAnalysisService(
                io.github.tissyboxc.clark_aams_backend.ai.AiConfigStore configStore,
                ObjectMapper objectMapper) {
            return new QuestionAnalysisService(configStore, objectMapper) {
                @Override
                public Map<String, Object> analyze(Map<String, Object> request) {
                    Object questionContent = request.get("question_content");
                    Object options = request.get("options");
                    if (questionContent == null || String.valueOf(questionContent).isBlank()) {
                        throw new io.github.tissyboxc.clark_aams_backend.common.BusinessException(
                                io.github.tissyboxc.clark_aams_backend.common.ErrorCode.BAD_REQUEST,
                                "question_content is required");
                    }
                    if (options == null) {
                        throw new io.github.tissyboxc.clark_aams_backend.common.BusinessException(
                                io.github.tissyboxc.clark_aams_backend.common.ErrorCode.BAD_REQUEST,
                                "options is required");
                    }
                    return Map.of("answer", "A");
                }
            };
        }
    }
}
