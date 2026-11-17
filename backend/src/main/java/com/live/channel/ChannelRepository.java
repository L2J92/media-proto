package com.live.channel;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    @EntityGraph(attributePaths = "owner")
    Optional<Channel> findByHandle(String handle);

    @EntityGraph(attributePaths = "owner")
    Optional<Channel> findByOwnerId(Long ownerId);

    boolean existsByHandle(String handle);

    @Query("select c.owner.id, c.handle from Channel c where c.owner.id in :ownerIds")
    List<Object[]> findHandles(@Param("ownerIds") Collection<Long> ownerIds);
}
