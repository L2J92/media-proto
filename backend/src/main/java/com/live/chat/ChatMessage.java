package com.live.chat;

import com.live.broadcast.Broadcast;
import com.live.common.BaseTimeEntity;
import com.live.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_broadcast", columnList = "broadcast_id, id"))
public class ChatMessage extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broadcast_id")
    private Broadcast broadcast;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false, length = 300)
    private String content;

    @Column(nullable = false)
    private boolean edited = false;

    private Instant deletedAt;

    protected ChatMessage() {}

    public ChatMessage(Broadcast broadcast, User author, String content) {
        this.broadcast = broadcast;
        this.author = author;
        this.content = content;
    }

    public boolean isWrittenBy(Long userId) {
        return author.getId().equals(userId);
    }

    public void edit(String content) {
        this.content = content;
        this.edited = true;
    }

    public void delete(Instant now) {
        this.deletedAt = now;
    }

    public Long getId() { return id; }
    public Broadcast getBroadcast() { return broadcast; }
    public User getAuthor() { return author; }
    public String getContent() { return content; }
    public boolean isEdited() { return edited; }
    public boolean isDeleted() { return deletedAt != null; }
}
