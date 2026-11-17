package com.live.channel;

import com.live.common.BaseTimeEntity;
import com.live.user.User;
import jakarta.persistence.*;

import java.time.Instant;

/** 채널 채팅 제재: until 이 null 이면 영구 차단, 아니면 그 시각까지 채팅 금지 */
@Entity
@Table(name = "channel_bans", indexes = @Index(name = "uk_channel_ban", columnList = "channel_id, user_id", unique = true))
public class ChannelBan extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private Instant until;

    @Column(length = 100)
    private String reason;

    @Column(nullable = false)
    private Long createdBy;

    protected ChannelBan() {}

    public ChannelBan(Channel channel, User user, Instant until, String reason, Long createdBy) {
        this.channel = channel;
        this.user = user;
        renew(until, reason, createdBy);
    }

    public void renew(Instant until, String reason, Long createdBy) {
        this.until = until;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    public boolean isActive(Instant now) { return until == null || until.isAfter(now); }

    public User getUser() { return user; }
    public Instant getUntil() { return until; }
    public String getReason() { return reason; }
}
