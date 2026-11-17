package com.live;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ModerationTest extends IntegrationTestSupport {

    private long userId(MockHttpSession s) throws Exception {
        return om.readTree(perform(get("/api/auth/me"), s, null).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long chat(MockHttpSession s, long broadcastId, String text) throws Exception {
        String body = perform(post("/api/broadcasts/" + broadcastId + "/chats"), s, "{\"content\":\"" + text + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("id").asLong();
    }

    @Test
    @DisplayName("채팅 금지 → 채팅 불가(403) + 실시간 알림, 해제 후 다시 가능, 다음 방송에도 제재 유지")
    void banAndUnban() throws Exception {
        MockHttpSession owner = signup("owner");
        MockHttpSession viewer = signup("viewer");
        long viewerId = userId(viewer);
        long id = createBroadcast(owner, "관리 방송");
        start(owner, id);
        chat(viewer, id, "hi");

        perform(post("/api/broadcasts/" + id + "/moderation/bans"), owner, "{\"userId\":" + viewerId + ",\"minutes\":10,\"reason\":\"도배\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.until").exists());
        perform(post("/api/broadcasts/" + id + "/chats"), viewer, "{\"content\":\"again\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CHAT_BANNED"));
        assertThat(realtime.published).anyMatch(s -> s.startsWith("broadcast:" + id + "|moderation|") && s.contains("USER_BANNED"));

        // 같은 채널의 다음 방송에도 유지
        long next = createBroadcast(owner, "다음 방송");
        perform(post("/api/broadcasts/" + next + "/chats"), viewer, "{\"content\":\"x\"}").andExpect(status().isForbidden());

        perform(get("/api/broadcasts/" + id + "/moderation"), owner, null)
                .andExpect(jsonPath("$.canModerate").value(true)).andExpect(jsonPath("$.bans[0].user.id").value(viewerId));
        perform(delete("/api/broadcasts/" + id + "/moderation/bans/" + viewerId), owner, null).andExpect(status().isNoContent());
        chat(viewer, id, "back");
    }

    @Test
    @DisplayName("매니저: 남의 채팅 삭제·제재 가능, 다른 매니저·소유자 제재와 매니저 지정은 불가")
    void managerPermissions() throws Exception {
        MockHttpSession owner = signup("owner");
        MockHttpSession manager = signup("manager");
        MockHttpSession manager2 = signup("manager2");
        MockHttpSession viewer = signup("viewer");
        long managerId = userId(manager), manager2Id = userId(manager2), viewerId = userId(viewer), ownerId = userId(owner);
        long id = createBroadcast(owner, "관리 방송");

        perform(put("/api/broadcasts/" + id + "/moderation/managers/" + managerId), owner, null).andExpect(status().isNoContent());
        perform(put("/api/broadcasts/" + id + "/moderation/managers/" + manager2Id), owner, null).andExpect(status().isNoContent());
        perform(put("/api/broadcasts/" + id + "/moderation/managers/" + viewerId), manager, null).andExpect(status().isForbidden());

        long chatId = chat(viewer, id, "광고");
        perform(delete("/api/chats/" + chatId), manager, null).andExpect(status().isNoContent());
        perform(post("/api/broadcasts/" + id + "/moderation/bans"), manager, "{\"userId\":" + viewerId + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.until").doesNotExist()); // 영구
        perform(post("/api/broadcasts/" + id + "/moderation/bans"), manager, "{\"userId\":" + manager2Id + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CANNOT_BAN"));
        perform(post("/api/broadcasts/" + id + "/moderation/bans"), manager, "{\"userId\":" + ownerId + "}")
                .andExpect(status().isBadRequest());
        perform(post("/api/broadcasts/" + id + "/moderation/bans"), viewer, "{\"userId\":" + managerId + "}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_MODERATOR"));

        // 일반 시청자는 관리 정보(목록)를 받지 못한다
        perform(get("/api/broadcasts/" + id + "/moderation"), viewer, null)
                .andExpect(jsonPath("$.canModerate").value(false)).andExpect(jsonPath("$.managers").isEmpty());
        perform(get("/api/broadcasts/" + id + "/moderation"), null, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("슬로우 모드: 시청자는 간격 안에 다시 보낼 수 없고(429), 관리자는 예외")
    void slowMode() throws Exception {
        MockHttpSession owner = signup("owner");
        MockHttpSession viewer = signup("viewer");
        long id = createBroadcast(owner, "슬로우 방송");
        perform(put("/api/broadcasts/" + id + "/moderation/slow-mode"), viewer, "{\"seconds\":30}").andExpect(status().isForbidden());
        perform(put("/api/broadcasts/" + id + "/moderation/slow-mode"), owner, "{\"seconds\":30}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.slowModeSeconds").value(30));

        chat(viewer, id, "1");
        perform(post("/api/broadcasts/" + id + "/chats"), viewer, "{\"content\":\"2\"}")
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("SLOW_MODE"));
        chat(owner, id, "a");
        chat(owner, id, "b");
        perform(get("/api/broadcasts/" + id + "/moderation"), null, null).andExpect(jsonPath("$.slowModeSeconds").value(30));
    }
}
