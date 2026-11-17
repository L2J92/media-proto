package com.live.donation;

import com.live.realtime.RealtimePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** 적립이 커밋된 후원만 방송 구독자에게 알린다. */
@Component
public class DonationRealtimeRelay {
    public static final String EVENT = "donation";
    private static final Logger log = LoggerFactory.getLogger(DonationRealtimeRelay.class);

    private final RealtimePublisher realtime;

    public DonationRealtimeRelay(RealtimePublisher realtime) {
        this.realtime = realtime;
    }

    @TransactionalEventListener
    public void relay(DonationService.DonationRealtimeEvent e) {
        try {
            realtime.publish(RealtimePublisher.broadcast(e.broadcastId()), EVENT, e.donation());
        } catch (RuntimeException ex) {
            log.warn("Donation relay failed for broadcast {}", e.broadcastId(), ex);
        }
    }
}
