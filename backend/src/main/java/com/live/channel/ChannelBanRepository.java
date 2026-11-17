package com.live.channel;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelBanRepository extends JpaRepository<ChannelBan, Long> {
    Optional<ChannelBan> findByChannelIdAndUserId(Long channelId, Long userId);

    @EntityGraph(attributePaths = "user")
    List<ChannelBan> findByChannelIdOrderByIdDesc(Long channelId);
}
