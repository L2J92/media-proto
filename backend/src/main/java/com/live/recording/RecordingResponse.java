package com.live.recording;

import java.time.Instant;

public record RecordingResponse(Long id, Long broadcastId, String title, String ownerNickname,
                                RecordingStatus status, Long durationMillis, String videoUrl,
                                Instant recordedAt) {
    public static RecordingResponse from(Recording r, String videoUrl) {
        var b = r.getBroadcast();
        return new RecordingResponse(r.getId(), b.getId(), b.getTitle(), b.getOwner().getNickname(),
                r.getStatus(), r.getDurationMillis(), videoUrl, r.getCreatedAt());
    }
}
