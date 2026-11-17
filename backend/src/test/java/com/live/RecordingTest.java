package com.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.live.media.MediaEvent;
import com.live.media.MediaServer.RecordingState;
import com.live.recording.RecordingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "recording.enabled=true",
        "recording.s3.bucket=vod",
        "recording.s3.public-base-url=https://cdn.test/vod",
        "recording.reconcile-interval=1h"
})
class RecordingTest extends IntegrationTestSupport {
    @Autowired RecordingService recordingService;

    private JsonNode recordingsOf(long broadcastId) throws Exception {
        return om.readTree(perform(get("/api/broadcasts/" + broadcastId + "/recordings"), null, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("방송 시작 시 HLS 녹화 시작 → 종료 후 상태 조회로 확정 → 플레이리스트 공개 URL 로 재생")
    void hlsRecordingLifecycle() throws Exception {
        MockHttpSession owner = signup("owner");
        long id = createBroadcast(owner, "녹화 방송");
        media.nextEgressId = "EG_1";
        start(owner, id);

        String playlist = media.startedRecordings.get("EG_1");
        assertThat(playlist).startsWith("recordings/broadcast-" + id + "/").endsWith("/index.m3u8");
        assertThat(recordingsOf(id).get(0).get("status").asText()).isEqualTo("RECORDING");

        perform(post("/api/broadcasts/" + id + "/end"), owner, null).andExpect(status().isOk());
        // 웹훅 없이도 스케줄러가 미디어 서버 상태를 조회해 확정
        media.recordingStates.put("EG_1", new RecordingState(true, true, "s3://vod/" + playlist, 65_000));
        recordingService.pendingRecordingIds().forEach(recordingService::reconcile);

        JsonNode rec = recordingsOf(id).get(0);
        assertThat(rec.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(rec.get("durationMillis").asLong()).isEqualTo(65_000);
        assertThat(rec.get("videoUrl").asText()).isEqualTo("https://cdn.test/vod/" + playlist);

        JsonNode all = om.readTree(perform(get("/api/recordings"), null, null).andReturn().getResponse().getContentAsString());
        assertThat(all).anyMatch(r -> r.get("broadcastId").asLong() == id);
    }

    @Test
    @DisplayName("방송 중에는 확정하지 않고, 웹훅과 스케줄러가 겹쳐도 한 번만 확정된다")
    void idempotentCompletion() throws Exception {
        MockHttpSession owner = signup("owner");
        long id = createBroadcast(owner, "녹화 방송");
        media.nextEgressId = "EG_2";
        start(owner, id);
        media.recordingStates.put("EG_2", new RecordingState(true, true, "loc", 10_000));

        recordingService.pendingRecordingIds().forEach(recordingService::reconcile);
        assertThat(recordingsOf(id).get(0).get("status").asText()).isEqualTo("RECORDING"); // 아직 방송 중

        perform(post("/api/broadcasts/" + id + "/end"), owner, null).andExpect(status().isOk());
        media.nextWebhook = new MediaEvent(MediaEvent.Type.EGRESS_ENDED, null, null, "EG_2", true, "loc", 10_000);
        mvc.perform(post("/api/webhooks/livekit").header("Authorization", "valid").content("{}")).andExpect(status().isOk());
        // 늦게 온 실패 통지는 이미 확정된 녹화를 바꾸지 않는다
        media.recordingStates.put("EG_2", new RecordingState(true, false, null, 0));
        recordingService.pendingRecordingIds().forEach(recordingService::reconcile);
        media.nextWebhook = new MediaEvent(MediaEvent.Type.EGRESS_ENDED, null, null, "EG_2", false, null, 0);
        mvc.perform(post("/api/webhooks/livekit").header("Authorization", "valid").content("{}")).andExpect(status().isOk());

        assertThat(recordingsOf(id).get(0).get("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void failedRecording() throws Exception {
        MockHttpSession owner = signup("owner");
        long id = createBroadcast(owner, "녹화 실패");
        media.nextEgressId = "EG_3";
        start(owner, id);
        perform(post("/api/broadcasts/" + id + "/end"), owner, null);
        media.recordingStates.put("EG_3", new RecordingState(true, false, null, 0));
        recordingService.pendingRecordingIds().forEach(recordingService::reconcile);

        JsonNode rec = recordingsOf(id).get(0);
        assertThat(rec.get("status").asText()).isEqualTo("FAILED");
        assertThat(rec.get("videoUrl").isNull()).isTrue();
    }
}
