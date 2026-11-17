package com.live.channel;

import com.live.broadcast.BroadcastDtos.BroadcastResponse;
import com.live.broadcast.BroadcastService;
import com.live.broadcast.OwnerHandleLookup;
import com.live.common.ApiException;
import com.live.common.ErrorCode;
import com.live.user.AuthUser;
import com.live.user.User;
import com.live.user.UserRegisteredEvent;
import com.live.user.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.live.channel.ChannelDtos.*;

@Service
public class ChannelService implements OwnerHandleLookup {
    private final ChannelRepository channelRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final BroadcastService broadcastService;

    public ChannelService(ChannelRepository channelRepository, FollowRepository followRepository,
                          UserRepository userRepository, @Lazy BroadcastService broadcastService) {
        this.channelRepository = channelRepository;
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.broadcastService = broadcastService;
    }

    /** 가입과 같은 트랜잭션에서 기본 채널(@u{회원ID})을 만든다 */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onUserRegistered(UserRegisteredEvent e) {
        User user = userRepository.findById(e.userId()).orElseThrow();
        channelRepository.save(new Channel(user, "u" + user.getId(), user.getNickname()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> handles(Collection<Long> ownerIds) {
        if (ownerIds.isEmpty()) return Map.of();
        Map<Long, String> map = new HashMap<>();
        for (Object[] row : channelRepository.findHandles(new HashSet<>(ownerIds))) {
            map.put((Long) row[0], (String) row[1]);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public ChannelResponse get(String handle, AuthUser viewer) {
        return toResponse(find(handle), viewer);
    }

    @Transactional(readOnly = true)
    public ChannelResponse mine(AuthUser user) {
        Channel c = channelRepository.findByOwnerId(user.id()).orElseThrow(() -> new ApiException(ErrorCode.CHANNEL_NOT_FOUND));
        return toResponse(c, user);
    }

    @Transactional
    public ChannelResponse update(AuthUser user, UpdateRequest req) {
        Channel c = channelRepository.findByOwnerId(user.id()).orElseThrow(() -> new ApiException(ErrorCode.CHANNEL_NOT_FOUND));
        String handle = req.handle().strip();
        if (!handle.equals(c.getHandle()) && channelRepository.existsByHandle(handle)) {
            throw new ApiException(ErrorCode.DUPLICATE_HANDLE);
        }
        c.update(handle, req.name().strip(), req.description() == null || req.description().isBlank() ? null : req.description().strip());
        try {
            channelRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.DUPLICATE_HANDLE);
        }
        return toResponse(c, user);
    }

    /** 팔로우 (이미 팔로우 중이면 그대로) */
    @Transactional
    public FollowResult follow(AuthUser user, String handle) {
        Channel c = find(handle);
        if (c.isOwnedBy(user.id())) throw new ApiException(ErrorCode.CANNOT_FOLLOW_SELF);
        if (!followRepository.existsByUserIdAndChannelId(user.id(), c.getId())) {
            User u = userRepository.findById(user.id()).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
            try {
                followRepository.saveAndFlush(new Follow(u, c));
            } catch (DataIntegrityViolationException ignored) {
                // 동시에 두 번 눌러도 하나만 남는다
            }
        }
        return new FollowResult(true, followRepository.countByChannelId(c.getId()));
    }

    /** 언팔로우 (팔로우 안 했으면 그대로) */
    @Transactional
    public FollowResult unfollow(AuthUser user, String handle) {
        Channel c = find(handle);
        followRepository.findByUserIdAndChannelId(user.id(), c.getId()).ifPresent(followRepository::delete);
        return new FollowResult(false, followRepository.countByChannelId(c.getId()));
    }

    /** 내가 팔로우한 채널 (방송 중인 채널 먼저) */
    @Transactional(readOnly = true)
    public List<FollowingItem> following(AuthUser user) {
        List<FollowingItem> items = new ArrayList<>();
        for (Follow f : followRepository.findFollowing(user.id())) {
            Channel c = f.getChannel();
            BroadcastResponse live = broadcastService.liveOf(c.getOwner().getId()).orElse(null);
            items.add(new FollowingItem(c.getHandle(), c.getName(), c.getOwner().getNickname(), live));
        }
        items.sort(Comparator.comparing((FollowingItem i) -> i.live() == null));
        return items;
    }

    /** 방송 시작 알림 대상 */
    @Transactional(readOnly = true)
    public Optional<Channel> channelOf(Long ownerId) {
        return channelRepository.findByOwnerId(ownerId);
    }

    @Transactional(readOnly = true)
    public List<Long> followerIds(Long channelId) {
        return followRepository.findFollowerIds(channelId);
    }

    private Channel find(String handle) {
        return channelRepository.findByHandle(handle).orElseThrow(() -> new ApiException(ErrorCode.CHANNEL_NOT_FOUND));
    }

    private ChannelResponse toResponse(Channel c, AuthUser viewer) {
        boolean following = viewer != null && followRepository.existsByUserIdAndChannelId(viewer.id(), c.getId());
        BroadcastResponse live = broadcastService.liveOf(c.getOwner().getId()).orElse(null);
        return new ChannelResponse(c.getId(), c.getHandle(), c.getName(), c.getDescription(),
                c.getOwner().getId(), c.getOwner().getNickname(), followRepository.countByChannelId(c.getId()),
                following, viewer != null && c.isOwnedBy(viewer.id()), live);
    }
}
