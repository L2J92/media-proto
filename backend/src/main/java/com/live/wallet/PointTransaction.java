package com.live.wallet;

import com.live.common.BaseTimeEntity;
import jakarta.persistence.*;

/** 포인트 원장. 잔액 변경마다 한 줄씩 남겨 내역 조회와 검증에 쓴다. */
@Entity
@Table(name = "point_transactions", indexes = @Index(name = "idx_point_tx_user", columnList = "userId, id"))
public class PointTransaction extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointTransactionType type;

    /** 증감액 (충전·받음은 +, 보냄은 -) */
    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long balanceAfter;

    /** 관련 충전 주문 또는 후원 ID */
    private Long refId;

    @Column(length = 100)
    private String description;

    protected PointTransaction() {}

    public PointTransaction(Long userId, PointTransactionType type, long amount, long balanceAfter, Long refId, String description) {
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.refId = refId;
        this.description = description;
    }

    public Long getId() { return id; }
    public PointTransactionType getType() { return type; }
    public long getAmount() { return amount; }
    public long getBalanceAfter() { return balanceAfter; }
    public Long getRefId() { return refId; }
    public String getDescription() { return description; }
}
