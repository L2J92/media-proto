package com.live.broadcast;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {

    @EntityGraph(attributePaths = "owner")
    @Query("select b from Broadcast b where b.id = :id and b.deletedAt is null")
    Optional<Broadcast> findActiveById(@Param("id") Long id);

    @EntityGraph(attributePaths = "owner")
    @Query(value = """
            select b from Broadcast b
            where b.deletedAt is null and (:status is null or b.status = :status)
            order by case when b.status = com.live.broadcast.BroadcastStatus.LIVE then 0 else 1 end, b.id desc
            """,
            countQuery = "select count(b) from Broadcast b where b.deletedAt is null and (:status is null or b.status = :status)")
    Page<Broadcast> search(@Param("status") BroadcastStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    @Query("select b from Broadcast b where b.owner.id = :ownerId and b.deletedAt is null order by b.id desc")
    List<Broadcast> findMine(@Param("ownerId") Long ownerId);

    @EntityGraph(attributePaths = "owner")
    Optional<Broadcast> findByRoomName(String roomName);

    List<Broadcast> findByStatus(BroadcastStatus status);

    List<Broadcast> findByStatusAndRoomClosedFalse(BroadcastStatus status);
}
