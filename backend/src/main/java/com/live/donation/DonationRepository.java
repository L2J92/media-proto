package com.live.donation;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DonationRepository extends JpaRepository<Donation, Long> {

    /** 승인 처리 동안 같은 주문에 대한 동시 요청(브라우저 재시도, 웹훅)을 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Donation d where d.orderId = :orderId")
    Optional<Donation> findByOrderIdForUpdate(@Param("orderId") String orderId);

    @EntityGraph(attributePaths = "donor")
    @Query("select d from Donation d where d.broadcast.id = :broadcastId and d.status = com.live.donation.DonationStatus.DONE order by d.approvedAt desc")
    List<Donation> findDone(@Param("broadcastId") Long broadcastId, Pageable pageable);

    @Query("select coalesce(sum(d.amount), 0) from Donation d where d.broadcast.id = :broadcastId and d.status = com.live.donation.DonationStatus.DONE")
    long sumDone(@Param("broadcastId") Long broadcastId);

    @Query("select count(d) from Donation d where d.broadcast.id = :broadcastId and d.status = com.live.donation.DonationStatus.DONE")
    long countDone(@Param("broadcastId") Long broadcastId);
}
