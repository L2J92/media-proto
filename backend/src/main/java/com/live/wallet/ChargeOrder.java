package com.live.wallet;

import com.live.common.BaseTimeEntity;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * 포인트 충전 결제 주문. 금액과 (바로 후원일 때) 후원 대상은 결제 전에 서버가 정해 저장한다.
 */
@Entity
@Table(name = "charge_orders", indexes = {
        @Index(name = "uk_charge_order", columnList = "orderId", unique = true),
        @Index(name = "uk_charge_payment", columnList = "paymentKey", unique = true)
})
public class ChargeOrder extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 64)
    private String orderId;

    @Column(nullable = false, length = 64)
    private String customerKey;

    /** 충전과 동시에 후원할 방송 (없으면 단순 충전) */
    private Long donationBroadcastId;

    @Column(length = 100)
    private String donationMessage;

    @Column(length = 200)
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChargeStatus status = ChargeStatus.PENDING;

    @Column(length = 200)
    private String failReason;

    private Instant approvedAt;

    @Version
    private Long version;

    protected ChargeOrder() {}

    public ChargeOrder(Long userId, long amount, String orderId, String customerKey,
                       Long donationBroadcastId, String donationMessage) {
        this.userId = userId;
        this.amount = amount;
        this.orderId = orderId;
        this.customerKey = customerKey;
        this.donationBroadcastId = donationBroadcastId;
        this.donationMessage = donationMessage;
    }

    /** 승인 확정. 같은 결제로 여러 번 호출돼도 한 번만 true. */
    public boolean complete(String paymentKey, Instant approvedAt) {
        if (status == ChargeStatus.DONE) return false;
        this.status = ChargeStatus.DONE;
        this.paymentKey = paymentKey;
        this.approvedAt = approvedAt;
        this.failReason = null;
        return true;
    }

    public void fail(String reason) {
        if (status != ChargeStatus.PENDING) return;
        this.status = ChargeStatus.FAILED;
        this.failReason = reason == null ? null : reason.substring(0, Math.min(200, reason.length()));
    }

    public boolean isOwnedBy(Long userId) { return this.userId.equals(userId); }
    public boolean isDone() { return status == ChargeStatus.DONE; }
    public boolean hasDonation() { return donationBroadcastId != null; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public long getAmount() { return amount; }
    public String getOrderId() { return orderId; }
    public String getCustomerKey() { return customerKey; }
    public Long getDonationBroadcastId() { return donationBroadcastId; }
    public String getDonationMessage() { return donationMessage; }
    public String getPaymentKey() { return paymentKey; }
    public ChargeStatus getStatus() { return status; }
}
