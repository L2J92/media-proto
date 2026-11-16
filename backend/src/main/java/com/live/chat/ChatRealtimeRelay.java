package com.live.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.live.media.MediaServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** DB 커밋이 끝난 채팅만 방송 방으로 전달한다. 전달 실패는 채팅 저장에 영향을 주지 않는다. */
@Component
public class ChatRealtimeRelay {
    public static final String TOPIC = "chat";
    private static final Logger log = LoggerFactory.getLogger(ChatRealtimeRelay.class);

    private final MediaServer mediaServer;
    private final ObjectMapper objectMapper;

    public ChatRealtimeRelay(MediaServer mediaServer, ObjectMapper objectMapper) {
        this.mediaServer = mediaServer;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener
    public void relay(ChatService.ChatRealtimeEvent e) {
        if (!mediaServer.isConfigured()) return;
        try {
            mediaServer.sendData(e.roomName(), TOPIC, objectMapper.writeValueAsBytes(e.event()));
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("Chat relay failed for room {}", e.roomName(), ex);
        }
    }
}
