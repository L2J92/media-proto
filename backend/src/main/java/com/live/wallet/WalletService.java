package com.live.wallet;

import com.live.common.ApiException;
import com.live.common.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class WalletService {
    private final WalletRepository walletRepository;
    private final PointTransactionRepository txRepository;
    private final WalletInitializer initializer;

    public WalletService(WalletRepository walletRepository, PointTransactionRepository txRepository,
                         WalletInitializer initializer) {
        this.walletRepository = walletRepository;
        this.txRepository = txRepository;
        this.initializer = initializer;
    }

    /** 충전. 호출하는 쪽 트랜잭션 안에서만 쓴다. @return 충전 후 잔액 */
    @Transactional(propagation = Propagation.MANDATORY)
    public long charge(Long userId, long amount, Long chargeOrderId) {
        Wallet w = lock(userId);
        long after = w.credit(amount);
        txRepository.save(new PointTransaction(userId, PointTransactionType.CHARGE, amount, after, chargeOrderId, "포인트 충전"));
        return after;
    }

    /**
     * 후원 포인트 이동. 두 지갑을 사용자 ID 순서로 잠가 교착을 막고, 잔액이 부족하면 아무것도 바꾸지 않는다.
     * @return 보낸 사람의 이동 후 잔액
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long transferForDonation(Long fromUserId, Long toUserId, long amount, Long donationId, String toName, String fromName) {
        Wallet first = lock(Math.min(fromUserId, toUserId));
        Wallet second = lock(Math.max(fromUserId, toUserId));
        Wallet from = first.getUserId().equals(fromUserId) ? first : second;
        Wallet to = from == first ? second : first;

        long fromAfter = from.debit(amount);
        long toAfter = to.credit(amount);
        txRepository.save(new PointTransaction(fromUserId, PointTransactionType.DONATION_SENT, -amount, fromAfter, donationId, toName + "님에게 후원"));
        txRepository.save(new PointTransaction(toUserId, PointTransactionType.DONATION_RECEIVED, amount, toAfter, donationId, fromName + "님의 후원"));
        return fromAfter;
    }

    @Transactional(readOnly = true)
    public WalletResponse get(Long userId) {
        long balance = walletRepository.findByUserId(userId).map(Wallet::getBalance).orElse(0L);
        List<TransactionResponse> recent = txRepository.findByUserIdOrderByIdDesc(userId, PageRequest.of(0, 30)).stream()
                .map(TransactionResponse::from).toList();
        return new WalletResponse(balance, recent);
    }

    private Wallet lock(Long userId) {
        return walletRepository.findByUserIdForUpdate(userId).orElseGet(() -> {
            try {
                initializer.createIfAbsent(userId);
            } catch (DataIntegrityViolationException ignored) {
                // 동시에 다른 요청이 먼저 만들었다
            }
            return walletRepository.findByUserIdForUpdate(userId)
                    .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        });
    }

    public record WalletResponse(long balance, List<TransactionResponse> transactions) {}

    public record TransactionResponse(Long id, PointTransactionType type, long amount, long balanceAfter,
                                      String description, Instant createdAt) {
        static TransactionResponse from(PointTransaction t) {
            return new TransactionResponse(t.getId(), t.getType(), t.getAmount(), t.getBalanceAfter(),
                    t.getDescription(), t.getCreatedAt());
        }
    }
}
