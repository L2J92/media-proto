package com.live.wallet;

import com.live.common.ApiException;
import com.live.common.BaseTimeEntity;
import com.live.common.ErrorCode;
import jakarta.persistence.*;

/** 계정별 포인트 지갑. 잔액 변경은 행 잠금(SELECT … FOR UPDATE) 상태에서만 한다. */
@Entity
@Table(name = "wallets", indexes = @Index(name = "uk_wallet_user", columnList = "userId", unique = true))
public class Wallet extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long balance = 0;

    @Version
    private Long version;

    protected Wallet() {}

    public Wallet(Long userId) {
        this.userId = userId;
    }

    public long credit(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        this.balance = Math.addExact(balance, amount);
        return balance;
    }

    public long debit(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        if (balance < amount) throw new ApiException(ErrorCode.INSUFFICIENT_POINTS);
        this.balance -= amount;
        return balance;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public long getBalance() { return balance; }
}
