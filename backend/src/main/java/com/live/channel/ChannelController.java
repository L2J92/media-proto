package com.live.channel;

import com.live.broadcast.BroadcastDtos.BroadcastResponse;
import com.live.broadcast.BroadcastDtos.PageResponse;
import com.live.broadcast.BroadcastService;
import com.live.common.ApiException;
import com.live.common.ErrorCode;
import com.live.user.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.live.channel.ChannelDtos.*;

@RestController
public class ChannelController {
    private final ChannelService channelService;
    private final BroadcastService broadcastService;

    public ChannelController(ChannelService channelService, BroadcastService broadcastService) {
        this.channelService = channelService;
        this.broadcastService = broadcastService;
    }

    @GetMapping("/api/channels/{handle}")
    public ChannelResponse get(@PathVariable String handle, @AuthenticationPrincipal AuthUser viewer) {
        return channelService.get(handle, viewer);
    }

    @GetMapping("/api/channels/{handle}/broadcasts")
    public PageResponse<BroadcastResponse> broadcasts(@PathVariable String handle,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        ChannelResponse c = channelService.get(handle, null);
        return broadcastService.byOwner(c.ownerId(), page, size);
    }

    @GetMapping("/api/me/channel")
    public ChannelResponse mine(@AuthenticationPrincipal AuthUser user) {
        return channelService.mine(requireLogin(user));
    }

    @PatchMapping("/api/me/channel")
    public ChannelResponse update(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody UpdateRequest req) {
        return channelService.update(requireLogin(user), req);
    }

    @PostMapping("/api/channels/{handle}/follow")
    public FollowResult follow(@AuthenticationPrincipal AuthUser user, @PathVariable String handle) {
        return channelService.follow(requireLogin(user), handle);
    }

    @DeleteMapping("/api/channels/{handle}/follow")
    public FollowResult unfollow(@AuthenticationPrincipal AuthUser user, @PathVariable String handle) {
        return channelService.unfollow(requireLogin(user), handle);
    }

    @GetMapping("/api/me/following")
    public List<FollowingItem> following(@AuthenticationPrincipal AuthUser user) {
        return channelService.following(requireLogin(user));
    }

    private static AuthUser requireLogin(AuthUser user) {
        if (user == null) throw new ApiException(ErrorCode.UNAUTHORIZED);
        return user;
    }
}
