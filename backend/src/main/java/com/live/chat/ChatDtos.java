package com.live.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ChatDtos {
    private ChatDtos() {}

    public record ChatRequest(@NotBlank(message = "내용을 입력하세요.") @Size(max = 300) String content) {}

    public record ChatResponse(Long id, Long broadcastId, Long authorId, String authorNickname,
                               String content, boolean edited, Instant createdAt) {
        public static ChatResponse from(ChatMessage c) {
            return new ChatResponse(c.getId(), c.getBroadcast().getId(), c.getAuthor().getId(),
                    c.getAuthor().getNickname(), c.getContent(), c.isEdited(), c.getCreatedAt());
        }
    }

    /** 방송 방에 실시간으로 뿌리는 이벤트. type: CREATED / UPDATED / DELETED */
    public record ChatEvent(String type, Long chatId, ChatResponse chat) {}
}
