package com.live.broadcast;

/** 방송이 시작됐을 때(트랜잭션 커밋 후 처리). 팔로워 알림 등에 쓴다. */
public record BroadcastStartedEvent(Long broadcastId, Long ownerId, String title) {}
