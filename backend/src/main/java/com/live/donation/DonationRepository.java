package com.live.donation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DonationRepository extends JpaRepository<Donation, Long> {

    @EntityGraph(attributePaths = {"donor", "streamer", "broadcast"})
    Optional<Donation> findByRequestId(String requestId);

    @EntityGraph(attributePaths = {"donor", "streamer", "broadcast"})
    Optional<Donation> findByChargeOrderId(Long chargeOrderId);

    @EntityGraph(attributePaths = {"donor", "streamer"})
    @Query("select d from Donation d where d.broadcast.id = :broadcastId order by d.id desc")
    List<Donation> findRecent(@Param("broadcastId") Long broadcastId, Pageable pageable);

    @Query("select coalesce(sum(d.amount), 0) from Donation d where d.broadcast.id = :broadcastId")
    long sumByBroadcast(@Param("broadcastId") Long broadcastId);

    long countByBroadcastId(Long broadcastId);
}
