package com.live.broadcast;

import com.live.media.MediaServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 웹훅이 유실되거나 설정되지 않은 환경에서도 방송 상태가 어긋나지 않도록 주기적으로 미디어 서버와 맞춘다.
 */
@Component
public class BroadcastPresenceScheduler {
    private static final Logger log = LoggerFactory.getLogger(BroadcastPresenceScheduler.class);

    private final BroadcastRepository broadcastRepository;
    private final BroadcastService broadcastService;
    private final MediaServer mediaServer;
    private final Duration grace;

    public BroadcastPresenceScheduler(BroadcastRepository broadcastRepository, BroadcastService broadcastService,
                                      MediaServer mediaServer,
                                      @Value("${broadcast.publisher-grace-period}") Duration grace) {
        this.broadcastRepository = broadcastRepository;
        this.broadcastService = broadcastService;
        this.mediaServer = mediaServer;
        this.grace = grace;
    }

    @Scheduled(fixedDelayString = "${broadcast.presence-check-interval}", initialDelayString = "10s")
    public void reconcile() {
        if (!mediaServer.isConfigured()) return;
        for (Broadcast b : broadcastRepository.findByStatus(BroadcastStatus.LIVE)) {
            try {
                broadcastService.reconcilePresence(b.getId(), grace);
            } catch (RuntimeException e) {
                log.warn("Presence check failed for broadcast {}", b.getId(), e);
            }
        }
        for (Broadcast b : broadcastRepository.findByStatusAndRoomClosedFalse(BroadcastStatus.ENDED)) {
            try {
                broadcastService.retryCloseRoom(b.getId());
            } catch (RuntimeException e) {
                log.warn("Room close retry failed for broadcast {}", b.getId(), e);
            }
        }
    }
}
