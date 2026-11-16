package com.live.broadcast;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class BroadcastDtos {
    private BroadcastDtos() {}

    public record UpsertRequest(
            @NotBlank(message = "제목을 입력하세요.") @Size(max = 100) String title,
            @Size(max = 1000) String description) {}

    public record StartRequest(@NotNull BroadcastSource source) {}

    public record OwnerDto(Long id, String nickname) {}

    public record BroadcastResponse(
            Long id, String title, String description, BroadcastStatus status, BroadcastSource source,
            OwnerDto owner, Instant createdAt, Instant startedAt, Instant endedAt) {
        public static BroadcastResponse from(Broadcast b) {
            return new BroadcastResponse(b.getId(), b.getTitle(), b.getDescription(), b.getStatus(), b.getSource(),
                    new OwnerDto(b.getOwner().getId(), b.getOwner().getNickname()),
                    b.getCreatedAt(), b.getStartedAt(), b.getEndedAt());
        }
    }

    /** 미디어 서버 접속 정보. role 은 publisher / viewer */
    public record ConnectionResponse(String serverUrl, String token, String roomName, String identity, String role) {}

    public record PageResponse<T>(java.util.List<T> items, int page, int size, long totalElements, int totalPages) {}
}
