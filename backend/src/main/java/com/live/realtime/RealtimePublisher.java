package com.live.realtime;

/**
 * 실시간 이벤트 전송 추상화 (이벤트 평면).
 * 영상·음성(미디어 평면)은 LiveKit 이 맡고, 채팅·후원·알림 같은 앱 이벤트는 이 인터페이스로 보낸다.
 * 방송이 없는 채널(종료된 방송의 댓글, 디스코드형 텍스트 채널, 개인 알림)에도 같은 방식으로 보낼 수 있다.
 *
 * 구현: {@link SseRealtimeHub} (단일 서버, SSE). 서버를 여러 대로 늘리면 Redis Pub/Sub 구현으로 교체한다.
 */
public interface RealtimePublisher {

    /**
     * @param channel 구독 채널 키 (예: broadcast:12, user:3)
     * @param event   이벤트 이름 (예: chat, donation, notification)
     * @param payload JSON 으로 직렬화할 객체
     */
    void publish(String channel, String event, Object payload);

    static String broadcast(Long broadcastId) { return "broadcast:" + broadcastId; }

    static String user(Long userId) { return "user:" + userId; }
}
