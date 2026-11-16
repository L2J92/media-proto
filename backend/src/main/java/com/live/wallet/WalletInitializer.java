package com.live.wallet;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 지갑이 없을 때 별도 트랜잭션으로 만든다. 동시에 만들어도 unique 제약으로 하나만 남는다. */
@Component
public class WalletInitializer {
    private final WalletRepository walletRepository;

    public WalletInitializer(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createIfAbsent(Long userId) {
        if (!walletRepository.existsByUserId(userId)) {
            walletRepository.saveAndFlush(new Wallet(userId));
        }
    }
}
