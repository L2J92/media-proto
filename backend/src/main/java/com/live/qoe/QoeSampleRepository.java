package com.live.qoe;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface QoeSampleRepository extends JpaRepository<QoeSample, Long> {
    @Query("select q from QoeSample q where q.broadcast.id = :broadcastId and q.createdAt >= :since order by q.createdAt")
    List<QoeSample> findSince(@Param("broadcastId") Long broadcastId, @Param("since") Instant since, Pageable pageable);

    @Modifying
    @Query("delete from QoeSample q where q.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
