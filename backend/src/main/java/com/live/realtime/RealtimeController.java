package com.live.realtime;

import com.live.broadcast.BroadcastRepository;
import com.live.common.ApiException;
import com.live.common.ErrorCode;
import com.live.user.AuthUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class RealtimeController {
    private final SseRealtimeHub hub;
    private final BroadcastRepository broadcastRepository;

    public RealtimeController(SseRealtimeHub hub, BroadcastRepository broadcastRepository) {
        this.hub = hub;
        this.broadcastRepository = broadcastRepository;
    }

    /** 방송별 이벤트(채팅·후원·관리). 비로그인도 구독 가능 */
    @GetMapping(value = "/api/realtime/broadcasts/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter broadcast(@PathVariable Long id) {
        if (broadcastRepository.findActiveById(id).isEmpty()) throw new ApiException(ErrorCode.BROADCAST_NOT_FOUND);
        return hub.subscribe(RealtimePublisher.broadcast(id));
    }

    /** 내 알림(팔로우한 채널 방송 시작 등). 로그인 필요 */
    @GetMapping(value = "/api/realtime/me", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter me(@AuthenticationPrincipal AuthUser user) {
        if (user == null) throw new ApiException(ErrorCode.UNAUTHORIZED);
        return hub.subscribe(RealtimePublisher.user(user.id()));
    }
}
