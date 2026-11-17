package com.live;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QoeTest extends IntegrationTestSupport {

    private String sample(String viewer, int kbps, double loss, double rtt, int freezes, String quality) {
        return """
                {"viewerKey":"%s","bitrateKbps":%d,"packetLossPct":%s,"jitterMs":12.5,"rttMs":%s,
                 "fps":29.5,"framesDropped":3,"freezeCount":%d,"frameHeight":720,"quality":"%s"}
                """.formatted(viewer, kbps, loss, rtt, freezes, quality);
    }

    @Test
    @DisplayName("비로그인 시청자도 샘플을 보내고, 방송자는 요약을 본다")
    void collectAndSummarize() throws Exception {
        MockHttpSession owner = signup("owner");
        long id = createBroadcast(owner, "품질 방송");
        start(owner, id);

        perform(post("/api/broadcasts/" + id + "/qoe"), null, sample("viewer-anon-a", 2500, 0.5, 40, 0, "auto"))
                .andExpect(status().isNoContent());
        perform(post("/api/broadcasts/" + id + "/qoe"), null, sample("viewer-anon-b", 500, 4.0, 200, 2, "low"))
                .andExpect(status().isNoContent());
        // 같은 시청자가 너무 자주 보내면 저장하지 않음
        perform(post("/api/broadcasts/" + id + "/qoe"), null, sample("viewer-anon-a", 9999, 50, 9999, 99, "auto"))
                .andExpect(status().isAccepted());

        JsonNode s = om.readTree(perform(get("/api/broadcasts/" + id + "/qoe"), owner, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(s.get("reportingViewers").asInt()).isEqualTo(2);
        assertThat(s.get("samples").asInt()).isEqualTo(2);
        assertThat(s.get("avgBitrateKbps").asDouble()).isEqualTo(1500.0);
        assertThat(s.get("avgPacketLossPct").asDouble()).isEqualTo(2.25);
        assertThat(s.get("p95RttMs").asDouble()).isEqualTo(200.0);
        assertThat(s.get("totalFreezes").asInt()).isEqualTo(2);
        assertThat(s.get("qualityShare").get("low").asDouble()).isEqualTo(50.0);
        assertThat(s.get("typicalHeight").asInt()).isEqualTo(720);
        assertThat(s.get("timeline")).isNotEmpty();
    }

    @Test
    void summaryIsOwnerOnly() throws Exception {
        MockHttpSession owner = signup("owner");
        long id = createBroadcast(owner, "품질 방송");
        perform(get("/api/broadcasts/" + id + "/qoe"), null, null).andExpect(status().isUnauthorized());
        perform(get("/api/broadcasts/" + id + "/qoe"), signup("other"), null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_BROADCAST_OWNER"));
        perform(get("/api/broadcasts/" + id + "/qoe"), owner, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.reportingViewers").value(0));
    }

    @Test
    void rejectsInvalidOrNotLive() throws Exception {
        MockHttpSession owner = signup("owner");
        long id = createBroadcast(owner, "품질 방송");
        perform(post("/api/broadcasts/" + id + "/qoe"), null, sample("viewer-anon-x", 1000, 0, 10, 0, "auto"))
                .andExpect(status().isConflict());
        start(owner, id);
        perform(post("/api/broadcasts/" + id + "/qoe"), null, sample("viewer-anon-x", 1000, 150, 10, 0, "auto"))
                .andExpect(status().isBadRequest());
        perform(post("/api/broadcasts/" + id + "/qoe"), null, sample("publisher-1", 1000, 0, 10, 0, "auto"))
                .andExpect(status().isBadRequest());
    }
}
