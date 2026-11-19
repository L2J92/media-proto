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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    @DisplayName("방송자가 방송 중에 녹화를 시작·중지하고, 중지한 녹화는 방송 중에도 다시보기로 확정된다")
    void manualStartStop() throws Exception {
        MockHttpSession owner = signup("owner");
        MockHttpSession other = signup("other");
        long id = createBroadcast(owner, "수동 녹화");
        // 자동 녹화 끄고 시작
        perform(post("/api/broadcasts/" + id + "/start"), owner, "{\"source\":\"CAMERA\",\"record\":false}")
                .andExpect(status().isOk());
        assertThat(recordingsOf(id)).isEmpty();

        media.nextEgressId = "EG_M1";
        perform(post("/api/broadcasts/" + id + "/recordings/start"), other, null).andExpect(status().isForbidden());
        perform(post("/api/broadcasts/" + id + "/recordings/start"), owner, null).andExpect(status().isOk());
        perform(post("/api/broadcasts/" + id + "/recordings/start"), owner, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RECORDING_ALREADY_ACTIVE"));

        perform(post("/api/broadcasts/" + id + "/recordings/stop"), owner, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.stopRequested").value(true));
        assertThat(media.stoppedRecordings).containsExactly("EG_M1");
        perform(post("/api/broadcasts/" + id + "/recordings/stop"), owner, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RECORDING_NOT_ACTIVE"));

        // 방송은 계속 중이지만, 중지한 녹화는 확정된다
        media.recordingStates.put("EG_M1", new RecordingState(true, true, "loc", 30_000));
        recordingService.pendingRecordingIds().forEach(recordingService::reconcile);
        assertThat(recordingsOf(id).get(0).get("status").asText()).isEqualTo("COMPLETED");

        // 다시 녹화 시작 가능 (두 번째 구간)
        media.nextEgressId = "EG_M2";
        perform(post("/api/broadcasts/" + id + "/recordings/start"), owner, null).andExpect(status().isOk());
        assertThat(recordingsOf(id)).hasSize(2);
    }

    @Test
    void recordingControlRequiresLive() throws Exception {
        MockHttpSession owner = signup("owner");
        long id = createBroadcast(owner, "대기 방송");
        perform(post("/api/broadcasts/" + id + "/recordings/start"), owner, null).andExpect(status().isConflict());
        perform(post("/api/broadcasts/" + id + "/recordings/start"), null, null).andExpect(status().isUnauthorized());
        perform(get("/api/config"), null, null).andExpect(jsonPath("$.recordingEnabled").value(true));
    }

    @Test
    void failedRecording() throws Exception {
        MockHttpSession owner = signup("owner");
        long id = createBroadcast(owner, "녹화 실패");
        media.nextEgressId = "EG_3";
        start(owner, id);
        perform(post("/api/broadcasts/" + id + "/end"), owner, null);
        media.recordingStates.put("EG_3", new RecordingState(true, false, null, 0, "S3 upload failed: access denied"));
        recordingService.pendingRecordingIds().forEach(recordingService::reconcile);

        JsonNode rec = recordingsOf(id).get(0);
        assertThat(rec.get("status").asText()).isEqualTo("FAILED");
        assertThat(rec.get("videoUrl").isNull()).isTrue();
        assertThat(rec.get("failReason").isNull()).isTrue(); // 비로그인에게는 숨김
        JsonNode ownerView = om.readTree(perform(get("/api/broadcasts/" + id + "/recordings"), owner, null)
                .andReturn().getResponse().getContentAsString()).get(0);
        assertThat(ownerView.get("failReason").asText()).contains("access denied");
    }
}
