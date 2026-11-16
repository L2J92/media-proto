package com.live.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChargeOrderRepository extends JpaRepository<ChargeOrder, Long> {
    /** 승인 처리 동안 같은 주문에 대한 동시 요청(브라우저 재시도, 웹훅)을 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChargeOrder c where c.orderId = :orderId")
    Optional<ChargeOrder> findByOrderIdForUpdate(@Param("orderId") String orderId);
}
