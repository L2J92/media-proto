package com.live.channel;

import com.live.broadcast.BroadcastStartedEvent;
import com.live.realtime.RealtimePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** 방송이 시작되면(커밋 후) 채널 팔로워들에게 실시간 알림을 보낸다. */
@Component
public class LiveNotificationRelay {
    private static final Logger log = LoggerFactory.getLogger(LiveNotificationRelay.class);

    private final ChannelService channelService;
    private final RealtimePublisher realtime;

    public LiveNotificationRelay(ChannelService channelService, RealtimePublisher realtime) {
        this.channelService = channelService;
        this.realtime = realtime;
    }

    @TransactionalEventListener
    public void onStarted(BroadcastStartedEvent e) {
        try {
            channelService.channelOf(e.ownerId()).ifPresent(c -> {
                var n = new ChannelDtos.LiveNotification("LIVE_STARTED", c.getHandle(), c.getName(), e.broadcastId(), e.title());
                for (Long followerId : channelService.followerIds(c.getId())) {
                    realtime.publish(RealtimePublisher.user(followerId), "notification", n);
                }
            });
        } catch (RuntimeException ex) {
            log.warn("Live notification failed for broadcast {}", e.broadcastId(), ex);
        }
    }
}
