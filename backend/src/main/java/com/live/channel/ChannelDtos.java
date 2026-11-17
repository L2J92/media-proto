package com.live.channel;

import com.live.broadcast.BroadcastDtos.BroadcastResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ChannelDtos {
    private ChannelDtos() {}

    public record ChannelResponse(Long id, String handle, String name, String description,
                                  Long ownerId, String ownerNickname, long followerCount, boolean following,
                                  boolean mine, BroadcastResponse live) {}

    public record UpdateRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9_]{3,30}", message = "채널 주소는 영문 소문자·숫자·_ 3~30자입니다.") String handle,
            @NotBlank @Size(min = 1, max = 50) String name,
            @Size(max = 500) String description) {}

    public record FollowResult(boolean following, long followerCount) {}

    /** 팔로잉 목록: 채널 + 지금 방송 중이면 그 방송 */
    public record FollowingItem(String handle, String name, String ownerNickname, BroadcastResponse live) {}

    /** 팔로워에게 보내는 실시간 알림 */
    public record LiveNotification(String type, String channelHandle, String channelName, Long broadcastId, String title) {}
}
