package com.live.channel;

import com.live.broadcast.Broadcast;
import com.live.broadcast.BroadcastRepository;
import com.live.common.ApiException;
import com.live.common.ErrorCode;
import com.live.realtime.RealtimePublisher;
import com.live.user.AuthUser;
import com.live.user.User;
import com.live.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.live.channel.ModerationDtos.*;

/**
 * 채널 채팅 관리. 방송의 채널 = 방송 소유자의 채널.
 * 관리자 = 채널 소유자 또는 매니저. 제재는 채널 단위라 다음 방송에도 유지된다.
 */
@Service
public class ModerationService {
    private final ChannelRepository channelRepository;
    private final ChannelManagerRepository managerRepository;
    private final ChannelBanRepository banRepository;
    private final BroadcastRepository broadcastRepository;
    private final UserRepository userRepository;
    private final RealtimePublisher realtime;
    private final Clock clock;

    public ModerationService(ChannelRepository channelRepository, ChannelManagerRepository managerRepository,
                             ChannelBanRepository banRepository, BroadcastRepository broadcastRepository,
                             UserRepository userRepository, RealtimePublisher realtime, Clock clock) {
        this.channelRepository = channelRepository;
        this.managerRepository = managerRepository;
        this.banRepository = banRepository;
        this.broadcastRepository = broadcastRepository;
        this.userRepository = userRepository;
        this.realtime = realtime;
        this.clock = clock;
    }

    // ---------- 채팅 작성 시 검사 (ChatService 에서 호출) ----------

    /** 제재 중이면 CHAT_BANNED */
    @Transactional(readOnly = true)
    public void checkCanChat(Long broadcastOwnerId, Long userId) {
        channelRepository.findByOwnerId(broadcastOwnerId).flatMap(c -> banRepository.findByChannelIdAndUserId(c.getId(), userId))
                .filter(b -> b.isActive(clock.instant()))
                .ifPresent(b -> { throw new ApiException(ErrorCode.CHAT_BANNED); });
    }

    /** 슬로우 모드 간격(초). 관리자는 0 */
    @Transactional(readOnly = true)
    public int slowModeSecondsFor(Long broadcastOwnerId, Long userId) {
        Channel c = channelRepository.findByOwnerId(broadcastOwnerId).orElse(null);
        if (c == null || c.getSlowModeSeconds() == 0 || isModerator(c, userId)) return 0;
        return c.getSlowModeSeconds();
    }

    @Transactional(readOnly = true)
    public boolean canModerate(Long broadcastOwnerId, Long userId) {
        if (userId == null) return false;
        return channelRepository.findByOwnerId(broadcastOwnerId).map(c -> isModerator(c, userId)).orElse(false);
    }

    // ---------- 관리 기능 ----------

    @Transactional(readOnly = true)
    public ModerationInfo info(AuthUser user, Long broadcastId) {
        Broadcast b = broadcast(broadcastId);
        Channel c = channelOf(b);
        boolean mod = user != null && isModerator(c, user.id());
        Instant now = clock.instant();
        List<UserRef> managers = mod ? managerRepository.findByChannelIdOrderById(c.getId()).stream()
                .map(m -> new UserRef(m.getUser().getId(), m.getUser().getNickname())).toList() : List.of();
        List<BanItem> bans = mod ? banRepository.findByChannelIdOrderByIdDesc(c.getId()).stream()
                .filter(x -> x.isActive(now))
                .map(x -> new BanItem(new UserRef(x.getUser().getId(), x.getUser().getNickname()), x.getUntil(), x.getReason()))
                .toList() : List.of();
        return new ModerationInfo(b.getOwner().getId(), mod, user != null && c.isOwnedBy(user.id()),
                c.getSlowModeSeconds(), managers, bans);
    }

    @Transactional
    public BanItem ban(AuthUser actor, Long broadcastId, BanRequest req) {
        Broadcast b = broadcast(broadcastId);
        Channel c = requireModerator(actor, b);
        User target = userRepository.findById(req.userId()).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        // 소유자는 제재할 수 없고, 매니저는 소유자만 제재할 수 있다
        if (c.isOwnedBy(target.getId()) || target.getId().equals(actor.id())
                || (managerRepository.existsByChannelIdAndUserId(c.getId(), target.getId()) && !c.isOwnedBy(actor.id()))) {
            throw new ApiException(ErrorCode.CANNOT_BAN);
        }
        Instant until = req.minutes() == null ? null : clock.instant().plus(Duration.ofMinutes(req.minutes()));
        String reason = req.reason() == null || req.reason().isBlank() ? null : req.reason().strip();
        ChannelBan ban = banRepository.findByChannelIdAndUserId(c.getId(), target.getId())
                .map(x -> { x.renew(until, reason, actor.id()); return x; })
                .orElseGet(() -> banRepository.save(new ChannelBan(c, target, until, reason, actor.id())));
        publishAfterCommit(b.getId(), new ModerationEvent("USER_BANNED", target.getId(), until, null));
        return new BanItem(new UserRef(target.getId(), target.getNickname()), ban.getUntil(), ban.getReason());
    }

    @Transactional
    public void unban(AuthUser actor, Long broadcastId, Long userId) {
        Broadcast b = broadcast(broadcastId);
        Channel c = requireModerator(actor, b);
        banRepository.findByChannelIdAndUserId(c.getId(), userId).ifPresent(banRepository::delete);
        publishAfterCommit(b.getId(), new ModerationEvent("USER_UNBANNED", userId, null, null));
    }

    @Transactional
    public int setSlowMode(AuthUser actor, Long broadcastId, int seconds) {
        Broadcast b = broadcast(broadcastId);
        Channel c = requireModerator(actor, b);
        c.changeSlowMode(seconds);
        publishAfterCommit(b.getId(), new ModerationEvent("SLOW_MODE", null, null, seconds));
        return seconds;
    }

    /** 매니저 지정·해제는 채널 소유자만 */
    @Transactional
    public void setManager(AuthUser owner, Long broadcastId, Long userId, boolean manager) {
        Broadcast b = broadcast(broadcastId);
        Channel c = channelOf(b);
        if (!c.isOwnedBy(owner.id())) throw new ApiException(ErrorCode.NOT_BROADCAST_OWNER);
        if (c.isOwnedBy(userId)) throw new ApiException(ErrorCode.CANNOT_BAN);
        if (manager) {
            if (!managerRepository.existsByChannelIdAndUserId(c.getId(), userId)) {
                User u = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
                managerRepository.save(new ChannelManager(c, u));
            }
        } else {
            managerRepository.findByChannelIdAndUserId(c.getId(), userId).ifPresent(managerRepository::delete);
        }
        publishAfterCommit(b.getId(), new ModerationEvent(manager ? "MANAGER_ADDED" : "MANAGER_REMOVED", userId, null, null));
    }

    // ---------- 내부 ----------

    private boolean isModerator(Channel c, Long userId) {
        return c.isOwnedBy(userId) || managerRepository.existsByChannelIdAndUserId(c.getId(), userId);
    }

    private Channel requireModerator(AuthUser actor, Broadcast b) {
        if (actor == null) throw new ApiException(ErrorCode.UNAUTHORIZED);
        Channel c = channelOf(b);
        if (!isModerator(c, actor.id())) throw new ApiException(ErrorCode.NOT_MODERATOR);
        return c;
    }

    private Broadcast broadcast(Long id) {
        return broadcastRepository.findActiveById(id).orElseThrow(() -> new ApiException(ErrorCode.BROADCAST_NOT_FOUND));
    }

    private Channel channelOf(Broadcast b) {
        return channelRepository.findByOwnerId(b.getOwner().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.CHANNEL_NOT_FOUND));
    }

    private void publishAfterCommit(Long broadcastId, ModerationEvent event) {
        Runnable send = () -> realtime.publish(RealtimePublisher.broadcast(broadcastId), "moderation", event);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else {
            send.run();
        }
    }
}
