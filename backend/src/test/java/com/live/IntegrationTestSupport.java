package com.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestMediaConfig.class)
public abstract class IntegrationTestSupport {
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper om;
    @Autowired protected FakeMediaServer media;

    @BeforeEach
    void resetMedia() { media.reset(); }

    /** 새 사용자로 가입하고 로그인된 세션을 돌려준다. */
    protected MockHttpSession signup(String nickname) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String email = UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        mvc.perform(post("/api/auth/signup").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Object[][]{{"email", email}, {"password", "password123"}, {"nickname", nickname}})))
                .andExpect(status().isCreated());
        return session;
    }

    protected long createBroadcast(MockHttpSession session, String title) throws Exception {
        String body = mvc.perform(post("/api/broadcasts").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(new Object[][]{{"title", title}})))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("id").asLong();
    }

    protected JsonNode start(MockHttpSession session, long id) throws Exception {
        String body = perform(post("/api/broadcasts/" + id + "/start"), session, "{\"source\":\"SCREEN\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(body);
    }

    protected ResultActions perform(MockHttpServletRequestBuilder req, MockHttpSession session, String jsonBody) throws Exception {
        if (session != null) req.session(session);
        if (jsonBody != null) req.contentType(MediaType.APPLICATION_JSON).content(jsonBody);
        return mvc.perform(req.with(csrf()));
    }

    protected String json(Object[][] pairs) throws Exception {
        var map = new java.util.LinkedHashMap<String, Object>();
        for (Object[] p : pairs) map.put((String) p[0], p[1]);
        return om.writeValueAsString(map);
    }
}
