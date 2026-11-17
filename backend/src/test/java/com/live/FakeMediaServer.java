package com.live;

import com.live.media.InvalidWebhookException;
import com.live.media.MediaEvent;
import com.live.media.MediaServer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 테스트용 미디어 서버. 호출 기록과 방 참가자를 메모리에 둔다. */
public class FakeMediaServer implements MediaServer {
    public final Set<String> rooms = ConcurrentHashMap.newKeySet();
    public final Map<String, Set<String>> participants = new ConcurrentHashMap<>();
    public final List<String> sentData = Collections.synchronizedList(new ArrayList<>());
    public boolean failDelete = false;
    public MediaEvent nextWebhook = MediaEvent.other();
    /** null 이 아니면 녹화를 시작한 것으로 보고 이 egressId 를 돌려준다 */
    public String nextEgressId = null;
    public final Map<String, String> startedRecordings = new ConcurrentHashMap<>(); // egressId -> playlistPath
    public final Map<String, RecordingState> recordingStates = new ConcurrentHashMap<>();

    public void reset() {
        rooms.clear(); participants.clear(); sentData.clear(); failDelete = false; nextWebhook = MediaEvent.other();
        nextEgressId = null; startedRecordings.clear(); recordingStates.clear();
    }

    @Override public boolean isConfigured() { return true; }
    @Override public String clientUrl() { return "wss://fake.livekit.test"; }
    @Override public void createRoom(String roomName) { rooms.add(roomName); }

    @Override public void deleteRoom(String roomName) {
        if (failDelete) throw new RuntimeException("delete failed");
        rooms.remove(roomName);
        participants.remove(roomName);
    }

    @Override public String issuePublisherToken(String roomName, String identity, String name) { return "pub:" + roomName + ":" + identity; }
    @Override public String issueViewerToken(String roomName, String identity, String name) { return "view:" + roomName + ":" + identity; }
    @Override public Set<String> participantIdentities(String roomName) { return participants.getOrDefault(roomName, Set.of()); }
    @Override public void sendData(String roomName, String topic, byte[] payload) { sentData.add(roomName + "|" + topic + "|" + new String(payload)); }
    @Override public Optional<String> startRecording(String roomName, String playlistPath) {
        if (nextEgressId == null) return Optional.empty();
        startedRecordings.put(nextEgressId, playlistPath);
        return Optional.of(nextEgressId);
    }
    @Override public Optional<RecordingState> findRecording(String egressId) { return Optional.ofNullable(recordingStates.get(egressId)); }

    @Override public MediaEvent parseWebhook(String body, String authorization) {
        if (!"valid".equals(authorization)) throw new InvalidWebhookException("bad", null);
        return nextWebhook;
    }
}
