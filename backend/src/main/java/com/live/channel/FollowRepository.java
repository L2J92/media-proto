package com.live.channel;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    boolean existsByUserIdAndChannelId(Long userId, Long channelId);

    Optional<Follow> findByUserIdAndChannelId(Long userId, Long channelId);

    long countByChannelId(Long channelId);

    @EntityGraph(attributePaths = {"channel", "channel.owner"})
    @Query("select f from Follow f where f.user.id = :userId order by f.id desc")
    List<Follow> findFollowing(@Param("userId") Long userId);

    @Query("select f.user.id from Follow f where f.channel.id = :channelId")
    List<Long> findFollowerIds(@Param("channelId") Long channelId);
}
