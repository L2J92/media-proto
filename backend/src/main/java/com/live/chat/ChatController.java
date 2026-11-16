package com.live.chat;

import com.live.user.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.live.chat.ChatDtos.*;

@RestController
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/api/broadcasts/{broadcastId}/chats")
    public List<ChatResponse> list(@PathVariable Long broadcastId, @RequestParam(defaultValue = "100") int limit) {
        return chatService.recent(broadcastId, limit);
    }

    @PostMapping("/api/broadcasts/{broadcastId}/chats")
    public ResponseEntity<ChatResponse> create(@AuthenticationPrincipal AuthUser user, @PathVariable Long broadcastId,
                                               @Valid @RequestBody ChatRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.create(user, broadcastId, req));
    }

    @PatchMapping("/api/chats/{chatId}")
    public ChatResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long chatId,
                               @Valid @RequestBody ChatRequest req) {
        return chatService.update(user, chatId, req);
    }

    @DeleteMapping("/api/chats/{chatId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long chatId) {
        chatService.delete(user, chatId);
        return ResponseEntity.noContent().build();
    }
}
