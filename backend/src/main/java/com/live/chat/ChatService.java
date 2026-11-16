package com.live.chat;

import com.live.broadcast.Broadcast;
import com.live.broadcast.BroadcastRepository;
import com.live.common.ApiException;
import com.live.common.ErrorCode;
import com.live.user.AuthUser;
import com.live.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.live.chat.ChatDtos.*;

@Service
public class ChatService {
    private final ChatMessageRepository chatRepository;
    private final BroadcastRepository broadcastRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final ChatRateLimiter rateLimiter;
    private final Clock clock;

    public ChatService(ChatMessageRepository chatRepository, BroadcastRepository broadcastRepository,
                       UserRepository userRepository, ApplicationEventPublisher events,
                       ChatRateLimiter rateLimiter, Clock clock) {
        this.rateLimiter = rateLimiter;
        this.chatRepository = chatRepository;
        this.broadcastRepository = broadcastRepository;
        this.userRepository = userRepository;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ChatResponse> recent(Long broadcastId, int limit) {
        findBroadcast(broadcastId);
        List<ChatResponse> list = new ArrayList<>(chatRepository
                .findRecent(broadcastId, PageRequest.of(0, Math.clamp(limit, 1, 200)))
                .stream().map(ChatResponse::from).toList());
        Collections.reverse(list);
        return list;
    }

    @Transactional
    public ChatResponse create(AuthUser user, Long broadcastId, ChatRequest req) {
        Broadcast b = findBroadcast(broadcastId);
        rateLimiter.check(user.id());
        var author = userRepository.findById(user.id()).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        ChatMessage saved = chatRepository.save(new ChatMessage(b, author, req.content().strip()));
        ChatResponse res = ChatResponse.from(saved);
        publish(b, new ChatEvent("CREATED", saved.getId(), res));
        return res;
    }

    @Transactional
    public ChatResponse update(AuthUser user, Long chatId, ChatRequest req) {
        ChatMessage c = findOwned(user, chatId);
        c.edit(req.content().strip());
        ChatResponse res = ChatResponse.from(c);
        publish(c.getBroadcast(), new ChatEvent("UPDATED", c.getId(), res));
        return res;
    }

    @Transactional
    public void delete(AuthUser user, Long chatId) {
        ChatMessage c = findOwned(user, chatId);
        c.delete(clock.instant());
        publish(c.getBroadcast(), new ChatEvent("DELETED", c.getId(), null));
    }

    private void publish(Broadcast b, ChatEvent event) {
        if (b.isLive()) {
            events.publishEvent(new ChatRealtimeEvent(b.getRoomName(), event));
        }
    }

    private Broadcast findBroadcast(Long id) {
        return broadcastRepository.findActiveById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.BROADCAST_NOT_FOUND));
    }

    /** 존재 확인 → 작성자 확인. 작성자가 아니면 403. */
    private ChatMessage findOwned(AuthUser user, Long chatId) {
        ChatMessage c = chatRepository.findActiveById(chatId)
                .orElseThrow(() -> new ApiException(ErrorCode.CHAT_NOT_FOUND));
        if (!c.isWrittenBy(user.id())) {
            throw new ApiException(ErrorCode.NOT_CHAT_AUTHOR);
        }
        return c;
    }

    public record ChatRealtimeEvent(String roomName, ChatEvent event) {}
}
