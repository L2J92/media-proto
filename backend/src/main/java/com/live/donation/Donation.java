package com.live.donation;

import com.live.broadcast.Broadcast;
import com.live.common.BaseTimeEntity;
import com.live.user.User;
import jakarta.persistence.*;

/** 포인트 후원. 생성되는 순간 포인트 이동까지 같은 트랜잭션에서 끝난 상태다. */
@Entity
@Table(name = "point_donations", indexes = {
        @Index(name = "uk_donation_request", columnList = "requestId", unique = true),
        @Index(name = "uk_donation_charge", columnList = "chargeOrderId", unique = true),
        @Index(name = "idx_donation_broadcast", columnList = "broadcast_id, id")
})
public class Donation extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broadcast_id")
    private Broadcast broadcast;

    /** 후원 받는 사람(방송 소유자). 후원 시점에 고정한다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "streamer_id")
    private User streamer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donor_id")
    private User donor;

    @Column(nullable = false)
    private long amount;

    @Column(length = 100)
    private String message;

    /** 클라이언트가 만든 요청 ID. 버튼을 두 번 눌러도 후원은 한 번만 된다. */
    @Column(nullable = false, length = 64)
    private String requestId;

    /** "충전하고 바로 후원"으로 만들어진 경우 그 충전 주문 */
    private Long chargeOrderId;

    protected Donation() {}

    public Donation(Broadcast broadcast, User donor, long amount, String message, String requestId, Long chargeOrderId) {
        this.broadcast = broadcast;
        this.streamer = broadcast.getOwner();
        this.donor = donor;
        this.amount = amount;
        this.message = message;
        this.requestId = requestId;
        this.chargeOrderId = chargeOrderId;
    }

    public boolean isDonatedBy(Long userId) { return donor.getId().equals(userId); }

    public Long getId() { return id; }
    public Broadcast getBroadcast() { return broadcast; }
    public User getStreamer() { return streamer; }
    public User getDonor() { return donor; }
    public long getAmount() { return amount; }
    public String getMessage() { return message; }
    public String getRequestId() { return requestId; }
    public Long getChargeOrderId() { return chargeOrderId; }
}
