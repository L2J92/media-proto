package com.live.media;

import com.live.broadcast.BroadcastService;
import com.live.recording.RecordingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class LiveKitWebhookController {
    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookController.class);

    private final MediaServer mediaServer;
    private final BroadcastService broadcastService;
    private final RecordingService recordingService;

    public LiveKitWebhookController(MediaServer mediaServer, BroadcastService broadcastService,
                                    RecordingService recordingService) {
        this.mediaServer = mediaServer;
        this.broadcastService = broadcastService;
        this.recordingService = recordingService;
    }

    @PostMapping(value = "/api/webhooks/livekit", consumes = "*/*")
    public ResponseEntity<Void> receive(@RequestBody String body,
                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        MediaEvent event;
        try {
            event = mediaServer.parseWebhook(body, authorization);
        } catch (InvalidWebhookException e) {
            log.warn("Rejected webhook: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }
        switch (event.type()) {
            case PARTICIPANT_JOINED -> broadcastService.onPublisherJoined(event.roomName(), event.participantIdentity());
            case PARTICIPANT_LEFT -> broadcastService.onPublisherLeft(event.roomName(), event.participantIdentity());
            case ROOM_FINISHED -> broadcastService.onRoomFinished(event.roomName());
            case EGRESS_ENDED -> recordingService.onEgressEnded(event.egressId(), event.egressSucceeded(),
                    event.fileLocation(), event.durationMillis(), event.error());
            case OTHER -> { }
        }
        return ResponseEntity.ok().build();
    }
}
