package com.live.broadcast;

import com.live.common.ApiException;
import com.live.common.BaseTimeEntity;
import com.live.common.ErrorCode;
import com.live.user.User;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "broadcasts", indexes = {
        @Index(name = "idx_broadcast_status", columnList = "status"),
        @Index(name = "idx_broadcast_room", columnList = "roomName", unique = true)
})
public class Broadcast extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BroadcastStatus status = BroadcastStatus.READY;

    /** 미디어 서버의 방 이름. 시작할 때 정해진다. */
    @Column(length = 64)
    private String roomName;

    /** 방송 소스: PC 화면 공유 / 모바일 카메라 (표시용) */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private BroadcastSource source;

    private Instant startedAt;
    private Instant endedAt;

    /** 송출자가 방에 없는 것이 처음 확인된 시각. null 이면 접속 중. */
    private Instant publisherAbsentSince;

    /** 종료 후 미디어 서버의 방까지 정리됐는지. 실패 시 스케줄러가 재시도한다. */
    @Column(nullable = false)
    private boolean roomClosed = false;

    private Instant deletedAt;

    @Version
    private Long version;

    protected Broadcast() {}

    public Broadcast(User owner, String title, String description) {
        this.owner = owner;
        this.title = title;
        this.description = description;
    }

    public boolean isOwnedBy(Long userId) {
        return owner.getId().equals(userId);
    }

    public void updateInfo(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public void start(String roomName, BroadcastSource source, Instant now) {
        if (status != BroadcastStatus.READY) {
            throw new ApiException(ErrorCode.INVALID_BROADCAST_STATE);
        }
        this.status = BroadcastStatus.LIVE;
        this.roomName = roomName;
        this.source = source;
        this.startedAt = now;
        // 시작 직후에는 아직 접속 전이므로 "부재" 상태에서 유예 시간을 센다.
        this.publisherAbsentSince = now;
    }

    public void end(Instant now) {
        if (status != BroadcastStatus.LIVE) {
            throw new ApiException(ErrorCode.BROADCAST_NOT_LIVE);
        }
        this.status = BroadcastStatus.ENDED;
        this.endedAt = now;
        this.publisherAbsentSince = null;
    }

    public void markPublisherPresent() {
        if (status == BroadcastStatus.LIVE) {
            this.publisherAbsentSince = null;
        }
    }

    public void markPublisherAbsent(Instant now) {
        if (status == BroadcastStatus.LIVE && publisherAbsentSince == null) {
            this.publisherAbsentSince = now;
        }
    }

    public boolean publisherAbsentLongerThan(Duration grace, Instant now) {
        return status == BroadcastStatus.LIVE && publisherAbsentSince != null
                && publisherAbsentSince.plus(grace).isBefore(now);
    }

    public void markRoomClosed() {
        this.roomClosed = true;
    }

    public void softDelete(Instant now) {
        if (status == BroadcastStatus.LIVE) {
            throw new ApiException(ErrorCode.INVALID_BROADCAST_STATE);
        }
        this.deletedAt = now;
    }

    public boolean isDeleted() { return deletedAt != null; }
    public boolean isLive() { return status == BroadcastStatus.LIVE; }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BroadcastStatus getStatus() { return status; }
    public String getRoomName() { return roomName; }
    public BroadcastSource getSource() { return source; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Instant getPublisherAbsentSince() { return publisherAbsentSince; }
    public boolean isRoomClosed() { return roomClosed; }
}
