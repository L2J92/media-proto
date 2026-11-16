package com.live.media;

public record MediaEvent(
        Type type,
        String roomName,
        String participantIdentity,
        String egressId,
        boolean egressSucceeded,
        String fileLocation,
        long durationMillis) {

    public enum Type {
        PARTICIPANT_JOINED, PARTICIPANT_LEFT, ROOM_FINISHED, EGRESS_ENDED, OTHER
    }

    public static MediaEvent other() {
        return new MediaEvent(Type.OTHER, null, null, null, false, null, 0);
    }
}
