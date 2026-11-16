package com.live.donation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.live.media.MediaServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** 적립이 커밋된 후원만 방송 방에 알린다. */
@Component
public class DonationRealtimeRelay {
    public static final String TOPIC = "donation";
    private static final Logger log = LoggerFactory.getLogger(DonationRealtimeRelay.class);

    private final MediaServer mediaServer;
    private final ObjectMapper objectMapper;

    public DonationRealtimeRelay(MediaServer mediaServer, ObjectMapper objectMapper) {
        this.mediaServer = mediaServer;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener
    public void relay(DonationService.DonationRealtimeEvent e) {
        if (!mediaServer.isConfigured()) return;
        try {
            mediaServer.sendData(e.roomName(), TOPIC, objectMapper.writeValueAsBytes(e.donation()));
        } catch (Exception ex) {
            log.warn("Donation relay failed for room {}", e.roomName(), ex);
        }
    }
}
