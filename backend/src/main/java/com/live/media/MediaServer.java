package com.live.media;

import java.util.Optional;
import java.util.Set;

/**
 * 미디어 서버(SFU) 추상화. 실제 구현은 LiveKit 이지만, 도메인 로직은 이 인터페이스만 의존한다.
 * 테스트에서는 가짜 구현으로 바꿔 끼운다.
 */
public interface MediaServer {

    boolean isConfigured();

    /** 브라우저가 접속할 WebSocket URL (wss://...) */
    String clientUrl();

    void createRoom(String roomName);

    /** 방을 지우면 접속 중인 송출자·시청자 연결이 모두 끊기고 녹화도 끝난다. */
    void deleteRoom(String roomName);

    /** 송출 권한(카메라, 마이크, 화면 공유, 화면 공유 소리)이 있는 토큰 */
    String issuePublisherToken(String roomName, String identity, String displayName);

    /** 시청만 가능한 토큰. 미디어 서버 단에서도 송출이 거부된다. */
    String issueViewerToken(String roomName, String identity, String displayName);

    /** 현재 방에 접속한 참가자 identity 목록. 방이 없으면 빈 집합. */
    Set<String> participantIdentities(String roomName);

    /** 방 안의 모든 참가자에게 서버에서 데이터 메시지를 보낸다. */
    void sendData(String roomName, String topic, byte[] payload);

    /**
     * 방 전체 화면을 HLS(세그먼트 + 플레이리스트)로 녹화 시작. 녹화가 설정되지 않았으면 empty.
     * @param playlistPath 저장소 안의 플레이리스트 경로 (예: recordings/broadcast-1/1700000000/index.m3u8)
     * @return egressId
     */
    Optional<String> startRecording(String roomName, String playlistPath);

    /** 녹화 중지 요청. 이미 끝난 녹화면 무시한다. */
    void stopRecording(String egressId);

    /** 녹화 상태 조회 (웹훅 유실 대비). 아직 진행 중이면 finished=false. 알 수 없으면 empty. */
    Optional<RecordingState> findRecording(String egressId);

    record RecordingState(boolean finished, boolean succeeded, String location, long durationMillis, String error) {
        public RecordingState(boolean finished, boolean succeeded, String location, long durationMillis) {
            this(finished, succeeded, location, durationMillis, null);
        }
    }

    /** 웹훅 서명을 검증하고 필요한 정보만 꺼낸다. 서명이 틀리면 예외. */
    MediaEvent parseWebhook(String body, String authorization);
}
