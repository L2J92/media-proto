package com.live.recording;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecordingRepository extends JpaRepository<Recording, Long> {
    Optional<Recording> findByEgressId(String egressId);

    List<Recording> findByStatus(RecordingStatus status);

    @EntityGraph(attributePaths = {"broadcast", "broadcast.owner"})
    @Query("select r from Recording r where r.status = :status and r.broadcast.deletedAt is null order by r.id desc")
    List<Recording> findVisible(@Param("status") RecordingStatus status);

    @EntityGraph(attributePaths = {"broadcast", "broadcast.owner"})
    @Query("select r from Recording r where r.id = :id and r.broadcast.deletedAt is null")
    Optional<Recording> findVisibleById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"broadcast", "broadcast.owner"})
    @Query("select r from Recording r where r.broadcast.id = :broadcastId and r.broadcast.deletedAt is null order by r.id desc")
    List<Recording> findByBroadcast(@Param("broadcastId") Long broadcastId);
}
