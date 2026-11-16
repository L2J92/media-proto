package com.live.donation;

import com.live.broadcast.Broadcast;
import com.live.common.BaseTimeEntity;
import com.live.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "donations", indexes = {
        @Index(name = "uk_donation_order", columnList = "orderId", unique = true),
        @Index(name = "uk_donation_payment", columnList = "paymentKey", unique = true),
        @Index(name = "idx_donation_broadcast", columnList = "broadcast_id, status")
})
public class Donation extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broadcast_id")
    private Broadcast broadcast;

    /** 후원 받는 사람(방송 소유자). 방송 정보가 바뀌어도 후원 대상은 고정한다. */
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

    /** 서버가 만든 주문 번호. 결제사에 그대로 전달된다. */
    @Column(nullable = false, length = 64)
    private String orderId;

    @Column(nullable = false, length = 64)
    private String customerKey;

    /** 결제사가 발급한 결제 키. 승인된 뒤에만 채워진다. */
    @Column(length = 200)
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DonationStatus status = DonationStatus.PENDING;

    @Column(length = 200)
    private String failReason;

    private Instant approvedAt;

    @Version
    private Long version;

    protected Donation() {}

    public Donation(Broadcast broadcast, User donor, long amount, String message, String orderId, String customerKey) {
        this.broadcast = broadcast;
        this.streamer = broadcast.getOwner();
        this.donor = donor;
        this.amount = amount;
        this.message = message;
        this.orderId = orderId;
        this.customerKey = customerKey;
    }

    /** 승인 확정. 같은 결제로 여러 번 호출돼도 한 번만 적립된다. @return 이번 호출로 새로 적립됐는지 */
    public boolean complete(String paymentKey, Instant approvedAt) {
        if (status == DonationStatus.DONE) return false;
        this.status = DonationStatus.DONE;
        this.paymentKey = paymentKey;
        this.approvedAt = approvedAt;
        this.failReason = null;
        return true;
    }

    public void fail(String reason) {
        if (status != DonationStatus.PENDING) return;
        this.status = DonationStatus.FAILED;
        this.failReason = reason == null ? null : reason.substring(0, Math.min(200, reason.length()));
    }

    public boolean isDonatedBy(Long userId) { return donor.getId().equals(userId); }
    public boolean isDone() { return status == DonationStatus.DONE; }

    public Long getId() { return id; }
    public Broadcast getBroadcast() { return broadcast; }
    public User getStreamer() { return streamer; }
    public User getDonor() { return donor; }
    public long getAmount() { return amount; }
    public String getMessage() { return message; }
    public String getOrderId() { return orderId; }
    public String getCustomerKey() { return customerKey; }
    public String getPaymentKey() { return paymentKey; }
    public DonationStatus getStatus() { return status; }
    public Instant getApprovedAt() { return approvedAt; }
}
