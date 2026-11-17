package com.live.channel;

import com.live.common.BaseTimeEntity;
import com.live.user.User;
import jakarta.persistence.*;

/** 스트리머 채널. 회원마다 하나. 방송은 채널 소유자의 방송 회차로 본다. */
@Entity
@Table(name = "channels", indexes = @Index(name = "uk_channel_handle", columnList = "handle", unique = true))
public class Channel extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", unique = true)
    private User owner;

    /** 주소에 쓰는 고유 이름 (/c/{handle}) */
    @Column(nullable = false, length = 30)
    private String handle;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 500)
    private String description;

    /** 슬로우 모드: 시청자가 채팅을 보낼 수 있는 최소 간격(초). 0 이면 끔 */
    @Column(nullable = false)
    private int slowModeSeconds = 0;

    protected Channel() {}

    public Channel(User owner, String handle, String name) {
        this.owner = owner;
        this.handle = handle;
        this.name = name;
    }

    public void update(String handle, String name, String description) {
        this.handle = handle;
        this.name = name;
        this.description = description;
    }

    public void changeSlowMode(int seconds) { this.slowModeSeconds = seconds; }

    public int getSlowModeSeconds() { return slowModeSeconds; }

    public boolean isOwnedBy(Long userId) { return owner.getId().equals(userId); }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getHandle() { return handle; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
