package com.live;

import com.live.realtime.SseRealtimeHub;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RealtimeSseTest extends IntegrationTestSupport {
    @Autowired SseRealtimeHub hub;

    @Test
    @DisplayName("비로그인도 방송 이벤트를 SSE 로 구독하고, 발행된 이벤트를 받는다")
    void subscribeAndReceive() throws Exception {
        long id = createBroadcast(signup("owner"), "SSE 방송");
        MvcResult result = mvc.perform(get("/api/realtime/broadcasts/" + id))
                .andExpect(request().asyncStarted()).andReturn();
        assertThat(hub.subscriberCount("broadcast:" + id)).isEqualTo(1);

        hub.publish("broadcast:" + id, "chat", Map.of("type", "CREATED", "content", "hello"));

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:ready").contains("event:chat").contains("\"content\":\"hello\"");
    }

    @Test
    void unknownBroadcastIs404AndMeRequiresLogin() throws Exception {
        mvc.perform(get("/api/realtime/broadcasts/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/realtime/me")).andExpect(status().isUnauthorized());
    }
}
