package com.live.recording;

import java.time.Instant;

public record RecordingResponse(Long id, Long broadcastId, String title, String ownerNickname,
                                RecordingStatus status, Long durationMillis, String videoUrl,
                                Instant recordedAt, String failReason) {
    public static RecordingResponse from(Recording r, String videoUrl) {
        var b = r.getBroadcast();
        return new RecordingResponse(r.getId(), b.getId(), b.getTitle(), b.getOwner().getNickname(),
                r.getStatus(), r.getDurationMillis(), videoUrl, r.getCreatedAt(), null);
    }

    public RecordingResponse withFailReason(String reason) {
        return new RecordingResponse(id, broadcastId, title, ownerNickname, status, durationMillis, videoUrl, recordedAt, reason);
    }
}
