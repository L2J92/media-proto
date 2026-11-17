package com.live.channel;

import com.live.common.BaseTimeEntity;
import com.live.user.User;
import jakarta.persistence.*;

/** 채널 매니저: 소유자 대신 채팅을 관리할 수 있다 */
@Entity
@Table(name = "channel_managers", indexes = @Index(name = "uk_channel_manager", columnList = "channel_id, user_id", unique = true))
public class ChannelManager extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    protected ChannelManager() {}

    public ChannelManager(Channel channel, User user) {
        this.channel = channel;
        this.user = user;
    }

    public User getUser() { return user; }
}
