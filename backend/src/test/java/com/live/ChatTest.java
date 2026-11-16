package com.live;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatTest extends IntegrationTestSupport {

    @Test
    void chatPermissions() throws Exception {
        MockHttpSession owner = signup("owner");
        MockHttpSession author = signup("author");
        MockHttpSession other = signup("other");
        long id = createBroadcast(owner, "채팅 방송");
        start(owner, id);

        perform(post("/api/broadcasts/" + id + "/chats"), null, "{\"content\":\"익명\"}").andExpect(status().isUnauthorized());

        String body = perform(post("/api/broadcasts/" + id + "/chats"), author, "{\"content\":\"안녕하세요\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long chatId = om.readTree(body).get("id").asLong();
        assertThat(media.sentData).anyMatch(s -> s.contains("CREATED") && s.contains("안녕하세요"));

        // 비로그인도 조회 가능
        perform(get("/api/broadcasts/" + id + "/chats"), null, null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));

        perform(patch("/api/chats/" + chatId), other, "{\"content\":\"변조\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_CHAT_AUTHOR"));
        perform(delete("/api/chats/" + chatId), other, null).andExpect(status().isForbidden());
        // 방송 소유자라도 남의 채팅은 수정 불가
        perform(patch("/api/chats/" + chatId), owner, "{\"content\":\"변조\"}").andExpect(status().isForbidden());

        perform(patch("/api/chats/" + chatId), author, "{\"content\":\"수정했어요\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.edited").value(true));
        perform(delete("/api/chats/" + chatId), author, null).andExpect(status().isNoContent());

        JsonNode list = om.readTree(perform(get("/api/broadcasts/" + id + "/chats"), null, null)
                .andReturn().getResponse().getContentAsString());
        assertThat(list).isEmpty();
        perform(delete("/api/chats/" + chatId), author, null).andExpect(status().isNotFound());
    }

    @Test
    void floodingIsRateLimited() throws Exception {
        MockHttpSession owner = signup("owner");
        long id = createBroadcast(owner, "도배 방송");
        for (int i = 0; i < 5; i++) {
            perform(post("/api/broadcasts/" + id + "/chats"), owner, "{\"content\":\"msg" + i + "\"}").andExpect(status().isCreated());
        }
        perform(post("/api/broadcasts/" + id + "/chats"), owner, "{\"content\":\"too fast\"}")
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("TOO_MANY_CHATS"));
    }

    @Test
    void emptyChatRejected() throws Exception {
        MockHttpSession owner = signup("owner");
        long id = createBroadcast(owner, "방송");
        perform(post("/api/broadcasts/" + id + "/chats"), owner, "{\"content\":\"  \"}").andExpect(status().isBadRequest());
    }
}
