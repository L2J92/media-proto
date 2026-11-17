package com.live.channel;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ModerationDtos {
    private ModerationDtos() {}

    /** minutes 가 null 이면 영구 차단 */
    public record BanRequest(@NotNull Long userId, @Min(1) @Max(10080) Integer minutes, @Size(max = 100) String reason) {}

    public record SlowModeRequest(@Min(0) @Max(300) int seconds) {}

    public record UserRef(Long id, String nickname) {}

    public record BanItem(UserRef user, Instant until, String reason) {}

    /** 채팅창에 필요한 관리 정보. managers/bans 는 관리자에게만 채워 준다 */
    public record ModerationInfo(Long ownerId, boolean canModerate, boolean isOwner, int slowModeSeconds,
                                 List<UserRef> managers, List<BanItem> bans) {}

    /** 실시간 관리 이벤트 (SSE "moderation") */
    public record ModerationEvent(String type, Long userId, Instant until, Integer slowModeSeconds) {}
}
