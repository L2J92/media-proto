package com.live.broadcast;

import com.live.user.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.live.broadcast.BroadcastDtos.*;

@RestController
@RequestMapping("/api/broadcasts")
public class BroadcastController {
    private final BroadcastService broadcastService;

    public BroadcastController(BroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    @GetMapping
    public PageResponse<BroadcastResponse> list(@RequestParam(required = false) BroadcastStatus status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return broadcastService.list(status, page, size);
    }

    @GetMapping("/mine")
    public List<BroadcastResponse> mine(@AuthenticationPrincipal AuthUser user) {
        if (user == null) throw new com.live.common.ApiException(com.live.common.ErrorCode.UNAUTHORIZED);
        return broadcastService.mine(user);
    }

    @GetMapping("/{id}")
    public BroadcastResponse get(@PathVariable Long id) {
        return broadcastService.get(id);
    }

    @PostMapping
    public ResponseEntity<BroadcastResponse> create(@AuthenticationPrincipal AuthUser user,
                                                    @Valid @RequestBody UpsertRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(broadcastService.create(user, req));
    }

    @PatchMapping("/{id}")
    public BroadcastResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                    @Valid @RequestBody UpsertRequest req) {
        return broadcastService.update(user, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        broadcastService.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/start")
    public ConnectionResponse start(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                    @Valid @RequestBody StartRequest req) {
        return broadcastService.start(user, id, req);
    }

    @PostMapping("/{id}/publisher-token")
    public ConnectionResponse publisherToken(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return broadcastService.publisherToken(user, id);
    }

    @PostMapping("/{id}/end")
    public BroadcastResponse end(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return broadcastService.end(user, id);
    }

    /** 비로그인도 GET 으로 시청 토큰을 받는다. 시청 전용 권한만 담긴다. */
    @GetMapping("/{id}/viewer-token")
    public ConnectionResponse viewerToken(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return broadcastService.viewerToken(id, user);
    }
}
