package com.live;

import com.live.realtime.RealtimePublisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 테스트용 실시간 전송: 보낸 이벤트를 "채널|이벤트|payload" 문자열로 기록한다. */
public class FakeRealtime implements RealtimePublisher {
    public final List<String> published = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void publish(String channel, String event, Object payload) {
        published.add(channel + "|" + event + "|" + payload);
    }
}
