package com.live;

import com.live.broadcast.BroadcastRepository;
import com.live.broadcast.BroadcastStatus;
import com.live.media.MediaEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookTest extends IntegrationTestSupport {
    @Autowired BroadcastRepository broadcastRepository;

    @Test
    void rejectsInvalidSignature() throws Exception {
        mvc.perform(post("/api/webhooks/livekit").header("Authorization", "forged").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roomFinishedEndsBroadcast() throws Exception {
        MockHttpSession owner = signup("owner");
        long id = createBroadcast(owner, "웹훅");
        String room = start(owner, id).get("roomName").asText();

        media.nextWebhook = new MediaEvent(MediaEvent.Type.ROOM_FINISHED, room, null, null, false, null, 0);
        mvc.perform(post("/api/webhooks/livekit").header("Authorization", "valid").content("{}"))
                .andExpect(status().isOk());

        assertThat(broadcastRepository.findById(id).orElseThrow().getStatus()).isEqualTo(BroadcastStatus.ENDED);
    }
}
