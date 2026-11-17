package com.live.chat;

import com.live.realtime.RealtimePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** DB 커밋이 끝난 채팅만 방송 구독자에게 전달한다. 전달 실패는 채팅 저장에 영향을 주지 않는다. */
@Component
public class ChatRealtimeRelay {
    public static final String EVENT = "chat";
    private static final Logger log = LoggerFactory.getLogger(ChatRealtimeRelay.class);

    private final RealtimePublisher realtime;

    public ChatRealtimeRelay(RealtimePublisher realtime) {
        this.realtime = realtime;
    }

    @TransactionalEventListener
    public void relay(ChatService.ChatRealtimeEvent e) {
        try {
            realtime.publish(RealtimePublisher.broadcast(e.broadcastId()), EVENT, e.event());
        } catch (RuntimeException ex) {
            log.warn("Chat relay failed for broadcast {}", e.broadcastId(), ex);
        }
    }
}
