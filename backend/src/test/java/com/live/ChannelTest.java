package com.live;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChannelTest extends IntegrationTestSupport {

    private JsonNode json(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return om.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String myHandle(MockHttpSession s) throws Exception {
        return json(perform(get("/api/me/channel"), s, null).andExpect(status().isOk())).get("handle").asText();
    }

    @Test
    @DisplayName("가입하면 기본 채널이 생기고, 주소·이름을 바꿀 수 있다 (중복·형식 검증)")
    void channelCreatedAndEditable() throws Exception {
        MockHttpSession a = signup("스트리머A");
        MockHttpSession b = signup("스트리머B");
        assertThat(myHandle(a)).matches("u\\d+");

        String unique = "chan_" + System.nanoTime() % 100000;
        perform(patch("/api/me/channel"), a, "{\"handle\":\"" + unique + "\",\"name\":\"A의 방송국\",\"description\":\"소개\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.handle").value(unique)).andExpect(jsonPath("$.mine").value(true));
        perform(patch("/api/me/channel"), b, "{\"handle\":\"" + unique + "\",\"name\":\"B\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_HANDLE"));
        perform(patch("/api/me/channel"), b, "{\"handle\":\"No Spaces!\",\"name\":\"B\"}").andExpect(status().isBadRequest());

        perform(get("/api/channels/" + unique), null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("A의 방송국")).andExpect(jsonPath("$.following").value(false));
        perform(get("/api/channels/nope_nope"), null, null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("팔로우·언팔로우는 멱등이고, 자기 채널은 팔로우할 수 없다")
    void followUnfollow() throws Exception {
        MockHttpSession streamer = signup("streamer");
        MockHttpSession fan = signup("fan");
        String handle = myHandle(streamer);

        perform(post("/api/channels/" + handle + "/follow"), null, null).andExpect(status().isUnauthorized());
        perform(post("/api/channels/" + handle + "/follow"), streamer, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CANNOT_FOLLOW_SELF"));

        perform(post("/api/channels/" + handle + "/follow"), fan, null).andExpect(jsonPath("$.followerCount").value(1));
        perform(post("/api/channels/" + handle + "/follow"), fan, null).andExpect(jsonPath("$.followerCount").value(1));
        perform(get("/api/channels/" + handle), fan, null).andExpect(jsonPath("$.following").value(true));

        perform(delete("/api/channels/" + handle + "/follow"), fan, null)
                .andExpect(jsonPath("$.following").value(false)).andExpect(jsonPath("$.followerCount").value(0));
        perform(delete("/api/channels/" + handle + "/follow"), fan, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("팔로우한 채널이 방송을 시작하면 팔로워에게 알림, 팔로잉·채널에 방송 중 표시")
    void liveNotificationAndFollowing() throws Exception {
        MockHttpSession streamer = signup("streamer");
        MockHttpSession fan = signup("fan");
        String handle = myHandle(streamer);
        long fanId = json(perform(get("/api/auth/me"), fan, null)).get("id").asLong();
        perform(post("/api/channels/" + handle + "/follow"), fan, null).andExpect(status().isOk());

        long id = createBroadcast(streamer, "첫 방송");
        start(streamer, id);

        assertThat(realtime.published).anyMatch(s -> s.startsWith("user:" + fanId + "|notification|")
                && s.contains("LIVE_STARTED") && s.contains(handle));
        perform(get("/api/channels/" + handle), null, null).andExpect(jsonPath("$.live.id").value(id));
        perform(get("/api/me/following"), fan, null)
                .andExpect(jsonPath("$[0].handle").value(handle)).andExpect(jsonPath("$[0].live.id").value(id));
        perform(get("/api/channels/" + handle + "/broadcasts"), null, null).andExpect(jsonPath("$.items[0].id").value(id));
        perform(get("/api/broadcasts/" + id), null, null).andExpect(jsonPath("$.owner.channelHandle").value(handle));
    }
}
