package com.live.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-Sent Events 로 구독자에게 이벤트를 보낸다.
 * - 가상 스레드라 연결을 오래 붙잡아도 플랫폼 스레드를 점유하지 않는다.
 * - 20초마다 하트비트를 보내 프록시가 유휴 연결을 끊지 않게 하고, 끊긴 연결을 정리한다.
 * - 연결은 30분마다 닫히고 브라우저(EventSource)가 자동으로 다시 붙는다.
 */
@Component
public class SseRealtimeHub implements RealtimePublisher {
    private static final Logger log = LoggerFactory.getLogger(SseRealtimeHub.class);
    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final ObjectMapper objectMapper;
    private final Map<String, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseRealtimeHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter subscribe(String channel) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Set<SseEmitter> set = subscribers.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet());
        set.add(emitter);
        Runnable remove = () -> {
            set.remove(emitter);
            if (set.isEmpty()) subscribers.remove(channel, set);
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            // 연결 직후 한 번 보내 응답을 열고, 재연결 간격(3초)을 알려준다.
            emitter.send(SseEmitter.event().name("ready").reconnectTime(3000).data("ok"));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    @Override
    public void publish(String channel, String event, Object payload) {
        Set<SseEmitter> set = subscribers.get(channel);
        if (set == null || set.isEmpty()) return;
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            log.warn("Realtime payload serialization failed for {}", event, e);
            return;
        }
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(event).data(json, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                set.remove(emitter);
                emitter.completeWithError(e);
            }
        }
    }

    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        subscribers.forEach((channel, set) -> {
            for (SseEmitter emitter : set) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    set.remove(emitter);
                }
            }
        });
    }

    /** 채널별 현재 구독자 수 (모니터링·테스트용) */
    public int subscriberCount(String channel) {
        Set<SseEmitter> set = subscribers.get(channel);
        return set == null ? 0 : set.size();
    }
}
