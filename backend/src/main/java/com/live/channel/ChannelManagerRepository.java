package com.live.channel;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelManagerRepository extends JpaRepository<ChannelManager, Long> {
    boolean existsByChannelIdAndUserId(Long channelId, Long userId);

    Optional<ChannelManager> findByChannelIdAndUserId(Long channelId, Long userId);

    @EntityGraph(attributePaths = "user")
    List<ChannelManager> findByChannelIdOrderById(Long channelId);
}
