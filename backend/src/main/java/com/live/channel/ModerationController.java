package com.live.channel;

import com.live.user.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.live.channel.ModerationDtos.*;

@RestController
@RequestMapping("/api/broadcasts/{broadcastId}/moderation")
public class ModerationController {
    private final ModerationService moderation;

    public ModerationController(ModerationService moderation) {
        this.moderation = moderation;
    }

    /** 누구나: 슬로우 모드·관리 권한 여부. 관리자에게는 매니저·제재 목록도 */
    @GetMapping
    public ModerationInfo info(@AuthenticationPrincipal AuthUser user, @PathVariable Long broadcastId) {
        return moderation.info(user, broadcastId);
    }

    @PostMapping("/bans")
    public BanItem ban(@AuthenticationPrincipal AuthUser user, @PathVariable Long broadcastId, @Valid @RequestBody BanRequest req) {
        return moderation.ban(user, broadcastId, req);
    }

    @DeleteMapping("/bans/{userId}")
    public ResponseEntity<Void> unban(@AuthenticationPrincipal AuthUser user, @PathVariable Long broadcastId, @PathVariable Long userId) {
        moderation.unban(user, broadcastId, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/slow-mode")
    public Map<String, Integer> slowMode(@AuthenticationPrincipal AuthUser user, @PathVariable Long broadcastId,
                                         @Valid @RequestBody SlowModeRequest req) {
        return Map.of("slowModeSeconds", moderation.setSlowMode(user, broadcastId, req.seconds()));
    }

    @PutMapping("/managers/{userId}")
    public ResponseEntity<Void> addManager(@AuthenticationPrincipal AuthUser user, @PathVariable Long broadcastId, @PathVariable Long userId) {
        moderation.setManager(user, broadcastId, userId, true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/managers/{userId}")
    public ResponseEntity<Void> removeManager(@AuthenticationPrincipal AuthUser user, @PathVariable Long broadcastId, @PathVariable Long userId) {
        moderation.setManager(user, broadcastId, userId, false);
        return ResponseEntity.noContent().build();
    }
}
