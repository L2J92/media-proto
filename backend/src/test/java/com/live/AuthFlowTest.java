package com.live;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthFlowTest extends IntegrationTestSupport {

    @Test
    @DisplayName("회원가입 → 내 정보 → 로그아웃 → 401 → 다시 로그인")
    void signupLogoutLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String signup = json(new Object[][]{{"email", "Auth@Test.com"}, {"password", "password123"}, {"nickname", "테스터"}});
        perform(post("/api/auth/signup"), session, signup).andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("auth@test.com"));
        perform(get("/api/auth/me"), session, null).andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("테스터"));

        perform(post("/api/auth/logout"), session, null).andExpect(status().isNoContent());
        perform(get("/api/auth/me"), session, null).andExpect(status().isUnauthorized());

        MockHttpSession again = new MockHttpSession();
        perform(post("/api/auth/login"), again, json(new Object[][]{{"email", "auth@test.com"}, {"password", "password123"}}))
                .andExpect(status().isOk());
        perform(get("/api/auth/me"), again, null).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        signupWith("wrong@test.com");
        perform(post("/api/auth/login"), new MockHttpSession(), json(new Object[][]{{"email", "wrong@test.com"}, {"password", "nope-nope"}}))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        signupWith("dup@test.com");
        perform(post("/api/auth/signup"), new MockHttpSession(),
                json(new Object[][]{{"email", "dup@test.com"}, {"password", "password123"}, {"nickname", "둘째"}}))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidSignupInputIsBadRequest() throws Exception {
        perform(post("/api/auth/signup"), new MockHttpSession(),
                json(new Object[][]{{"email", "not-email"}, {"password", "short"}, {"nickname", "a"}}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void demoLoginWorks() throws Exception {
        MockHttpSession s = new MockHttpSession();
        perform(post("/api/auth/demo-login?account=STREAMER"), s, null).andExpect(status().isOk());
        perform(get("/api/auth/me"), s, null).andExpect(jsonPath("$.email").value("streamer@demo.live"));
    }

    @Test
    @DisplayName("CSRF 토큰 없는 상태 변경 요청은 거부된다")
    void csrfRequired() throws Exception {
        MockHttpSession s = signup("csrf");
        mvc.perform(post("/api/broadcasts").session(s).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    private void signupWith(String email) throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Object[][]{{"email", email}, {"password", "password123"}, {"nickname", "사용자"}})))
                .andExpect(status().isCreated());
    }
}
