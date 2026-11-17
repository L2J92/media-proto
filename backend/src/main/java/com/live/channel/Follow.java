package com.live.channel;

import com.live.common.BaseTimeEntity;
import com.live.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "follows", indexes = {
        @Index(name = "uk_follow_user_channel", columnList = "user_id, channel_id", unique = true),
        @Index(name = "idx_follow_channel", columnList = "channel_id")
})
public class Follow extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    protected Follow() {}

    public Follow(User user, Channel channel) {
        this.user = user;
        this.channel = channel;
    }

    public User getUser() { return user; }
    public Channel getChannel() { return channel; }
}
