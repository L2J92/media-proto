package com.live.broadcast;

import com.live.media.MediaServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 방송 중인 방의 시청자 수. 목록 조회마다 미디어 서버를 부르지 않도록 짧게 캐시한다.
 * 조회에 실패하면 null (화면에 표시하지 않음).
 */
@Component
public class ViewerCountService {
    private static final Logger log = LoggerFactory.getLogger(ViewerCountService.class);
    private static final Duration TTL = Duration.ofSeconds(10);

    private final MediaServer mediaServer;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public ViewerCountService(MediaServer mediaServer, Clock clock) {
        this.mediaServer = mediaServer;
        this.clock = clock;
    }

    public Integer viewerCount(Broadcast b) {
        if (!b.isLive() || b.getRoomName() == null || !mediaServer.isConfigured()) return null;
        long now = clock.millis();
        Cached c = cache.get(b.getRoomName());
        if (c != null && now - c.at < TTL.toMillis()) return c.count;
        try {
            int count = (int) mediaServer.participantIdentities(b.getRoomName()).stream()
                    .filter(id -> id.startsWith("viewer-")).count();
            cache.put(b.getRoomName(), new Cached(count, now));
            return count;
        } catch (RuntimeException e) {
            log.debug("viewer count failed for {}", b.getRoomName(), e);
            return c == null ? null : c.count;
        }
    }

    private record Cached(int count, long at) {}
}
