package com.sms.api;

import static com.sms.api.ApiTestSupport.accessToken;
import static com.sms.api.ApiTestSupport.bearer;
import static com.sms.api.ApiTestSupport.loginBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.sms.Fixtures;
import com.sms.auth.ApiTokenRepository;
import com.sms.auth.UserRepository;
import com.sms.school.SchoolClass;

/** Token sign-in, refresh, logout and sign-up, and that the website's cookie login is unaffected. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:testdb?mode=memory&cache=shared&foreign_keys=on",
        "sms.demo-data.enabled=false",
        "sms.teacher.signup-code=teach-2026"})
@AutoConfigureMockMvc
@Transactional
class ApiAuthTest {

    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired UserRepository users;
    @Autowired ApiTokenRepository tokens;

    SchoolClass schoolClass;

    @BeforeEach
    void setUp() {
        schoolClass = fixtures.schoolClass("7", "A", null);
        fixtures.student("auth_stu", schoolClass, "1");
    }

    @Test
    void loginByUsernameOrEmailReturnsTokensAndUser() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"auth_stu@test.local\",\"password\":\"" + Fixtures.PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.username").value("auth_stu"))
                .andExpect(jsonPath("$.user.role").value("STUDENT"));

        mvc.perform(get("/api/v1/me").with(bearer(accessToken(mvc, "auth_stu"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.name").value("Student auth_stu"))
                .andExpect(jsonPath("$.student.schoolClass.name").value("7-A"))
                .andExpect(jsonPath("$.teacher").isEmpty());
    }

    @Test
    void wrongPasswordAndMissingTokenGiveJson401() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"auth_stu\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sign in to continue."));
        mvc.perform(get("/api/v1/me").with(bearer("not-a-real-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankLoginFieldsAreValidationErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"login\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.login").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void refreshReplacesBothTokensAndTheOldRefreshTokenStopsWorking() throws Exception {
        String first = loginBody(mvc, "auth_stu");
        String oldAccess = JsonPath.read(first, "$.accessToken");
        String oldRefresh = JsonPath.read(first, "$.refreshToken");

        String second = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newAccess = JsonPath.read(second, "$.accessToken");

        mvc.perform(get("/api/v1/me").with(bearer(newAccess))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").with(bearer(oldAccess))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        String body = loginBody(mvc, "auth_stu");
        String access = JsonPath.read(body, "$.accessToken");
        String refresh = JsonPath.read(body, "$.refreshToken");

        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me").with(bearer(access))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredAccessTokenIsRejected() throws Exception {
        String access = accessToken(mvc, "auth_stu");
        tokens.findAll().forEach(t -> t.setAccessExpiresAt(LocalDateTime.now().minusMinutes(1)));
        tokens.flush();

        mvc.perform(get("/api/v1/me").with(bearer(access))).andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountIsSignedOutAndCannotSignIn() throws Exception {
        String access = accessToken(mvc, "auth_stu");
        users.findByUsername("auth_stu").orElseThrow().setEnabled(false);
        users.flush();

        mvc.perform(get("/api/v1/me").with(bearer(access))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"auth_stu\",\"password\":\"" + Fixtures.PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changingPasswordSignsOutEveryDeviceAndReturnsANewSession() throws Exception {
        String phone = accessToken(mvc, "auth_stu");
        String tablet = accessToken(mvc, "auth_stu");

        String body = mvc.perform(post("/api/v1/me/password").with(bearer(phone))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + Fixtures.PASSWORD
                                + "\",\"newPassword\":\"new-password-1\",\"confirmPassword\":\"new-password-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/v1/me").with(bearer(phone))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").with(bearer(tablet))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").with(bearer(JsonPath.read(body, "$.accessToken")))).andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPasswordIsAFieldError() throws Exception {
        mvc.perform(post("/api/v1/me/password").with(bearer(accessToken(mvc, "auth_stu")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"nope\",\"newPassword\":\"new-password-1\","
                                + "\"confirmPassword\":\"new-password-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").value("Current password is incorrect"));
    }

    @Test
    void studentSignupFollowsTheWebsiteRules() throws Exception {
        mvc.perform(get("/api/v1/auth/signup-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classes[*].name", hasItem("7-A")))
                .andExpect(jsonPath("$.teacherSignupEnabled").value(true))
                .andExpect(jsonPath("$.adminSignupEnabled").value(false));

        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signup("STUDENT", "new_stu", "\"classId\":" + schoolClass.getId() + ",\"rollNo\":\"2\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STUDENT"));
        assertThat(accessToken(mvc, "new_stu")).isNotBlank();

        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signup("STUDENT", "dup_roll", "\"classId\":" + schoolClass.getId() + ",\"rollNo\":\"1\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.rollNo").exists());
    }

    @Test
    void teacherAndAdminSignupNeedInvitationCodes() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signup("TEACHER", "new_teacher", "\"teacherCode\":\"wrong\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.teacherCode").value("A valid teacher invitation code is required"));
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signup("TEACHER", "new_teacher", "\"teacherCode\":\"teach-2026\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("TEACHER"));

        // No admin code is configured, so admin sign-up is closed.
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signup("ADMIN", "new_admin", "\"adminCode\":\"anything\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.adminCode").exists());
    }

    @Test
    void signupValidationErrorsAreReportedPerField() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"username\":\"x\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void roleRulesApplyToTheApi() throws Exception {
        String student = accessToken(mvc, "auth_stu");
        mvc.perform(get("/api/v1/teacher/classes").with(bearer(student)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/api/v1/admin/overview").with(bearer(student))).andExpect(status().isForbidden());
    }

    @Test
    void websiteStillUsesItsOwnLoginAndCsrf() throws Exception {
        String student = accessToken(mvc, "auth_stu");
        // A bearer token does not sign anyone in to the website.
        mvc.perform(get("/student/dashboard").with(bearer(student)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        // Website forms still need a CSRF token.
        mvc.perform(post("/login").param("username", "auth_stu").param("password", Fixtures.PASSWORD))
                .andExpect(status().isForbidden());
    }

    private static String signup(String role, String username, String extra) {
        return "{\"role\":\"" + role + "\",\"username\":\"" + username + "\",\"email\":\"" + username
                + "@test.local\",\"password\":\"" + Fixtures.PASSWORD + "\",\"name\":\"Name " + username + "\","
                + extra + "}";
    }
}
