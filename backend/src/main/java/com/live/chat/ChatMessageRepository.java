package com.live.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 최신순으로 가져와 화면에서 뒤집는다. */
    @EntityGraph(attributePaths = "author")
    @Query("select c from ChatMessage c where c.broadcast.id = :broadcastId and c.deletedAt is null order by c.id desc")
    List<ChatMessage> findRecent(@Param("broadcastId") Long broadcastId, Pageable pageable);

    Optional<ChatMessage> findFirstByBroadcastIdAndAuthorIdOrderByIdDesc(Long broadcastId, Long authorId);

    @EntityGraph(attributePaths = {"author", "broadcast", "broadcast.owner"})
    @Query("select c from ChatMessage c where c.id = :id and c.deletedAt is null")
    Optional<ChatMessage> findActiveById(@Param("id") Long id);
}
