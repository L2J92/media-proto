package com.live.chat;

import com.live.common.ApiException;
import com.live.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자별 채팅 도배 방지 (슬라이딩 윈도우). 단일 인스턴스 서버라 메모리로 충분하다.
 */
@Component
public class ChatRateLimiter {
    private final int maxMessages;
    private final Duration window;
    private final Clock clock;
    private final Map<Long, Deque<Long>> history = new ConcurrentHashMap<>();

    public ChatRateLimiter(@Value("${chat.rate-limit.max-messages:5}") int maxMessages,
                           @Value("${chat.rate-limit.window:5s}") Duration window,
                           Clock clock) {
        this.maxMessages = maxMessages;
        this.window = window;
        this.clock = clock;
    }

    public void check(Long userId) {
        long now = clock.millis();
        long from = now - window.toMillis();
        Deque<Long> q = history.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() <= from) q.pollFirst();
            if (q.size() >= maxMessages) {
                throw new ApiException(ErrorCode.TOO_MANY_CHATS);
            }
            q.addLast(now);
        }
    }
}
