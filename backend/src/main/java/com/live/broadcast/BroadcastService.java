package com.live.broadcast;

import com.live.common.ApiException;
import com.live.common.ErrorCode;
import com.live.media.MediaServer;
import com.live.recording.RecordingService;
import com.live.user.AuthUser;
import com.live.user.User;
import com.live.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.live.broadcast.BroadcastDtos.*;

@Service
public class BroadcastService {
    private static final Logger log = LoggerFactory.getLogger(BroadcastService.class);

    private final BroadcastRepository broadcastRepository;
    private final UserRepository userRepository;
    private final MediaServer mediaServer;
    private final RecordingService recordingService;
    private final ViewerCountService viewerCounts;
    private final OwnerHandleLookup handleLookup;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final Clock clock;

    public BroadcastService(BroadcastRepository broadcastRepository, UserRepository userRepository,
                            MediaServer mediaServer, RecordingService recordingService,
                            ViewerCountService viewerCounts, OwnerHandleLookup handleLookup,
                            org.springframework.context.ApplicationEventPublisher events, Clock clock) {
        this.viewerCounts = viewerCounts;
        this.handleLookup = handleLookup;
        this.events = events;
        this.broadcastRepository = broadcastRepository;
        this.userRepository = userRepository;
        this.mediaServer = mediaServer;
        this.recordingService = recordingService;
        this.clock = clock;
    }

    public static String publisherIdentity(Long userId) {
        return "publisher-" + userId;
    }

    // ---------- 조회 (비로그인 허용) ----------

    @Transactional(readOnly = true)
    public PageResponse<BroadcastResponse> list(BroadcastStatus status, int page, int size) {
        Page<Broadcast> result = broadcastRepository.search(status, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50)));
        return toPage(result);
    }

    /** 채널 페이지: 한 사용자의 방송 회차 목록 */
    @Transactional(readOnly = true)
    public PageResponse<BroadcastResponse> byOwner(Long ownerId, int page, int size) {
        return toPage(broadcastRepository.findByOwner(ownerId, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50))));
    }

    /** 사용자가 지금 방송 중이면 그 방송 */
    @Transactional(readOnly = true)
    public java.util.Optional<BroadcastResponse> liveOf(Long ownerId) {
        return broadcastRepository.findLiveByOwner(ownerId).stream().findFirst().map(this::toResponse);
    }

    private PageResponse<BroadcastResponse> toPage(Page<Broadcast> result) {
        var handles = handleLookup.handles(result.getContent().stream().map(b -> b.getOwner().getId()).toList());
        return new PageResponse<>(result.map(b -> BroadcastResponse.from(b, viewerCounts.viewerCount(b),
                        handles.get(b.getOwner().getId()))).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public BroadcastResponse get(Long id) {
        return toResponse(find(id));
    }

    private BroadcastResponse toResponse(Broadcast b) {
        String handle = handleLookup.handles(java.util.List.of(b.getOwner().getId())).get(b.getOwner().getId());
        return BroadcastResponse.from(b, viewerCounts.viewerCount(b), handle);
    }

    @Transactional(readOnly = true)
    public List<BroadcastResponse> mine(AuthUser user) {
        return broadcastRepository.findMine(user.id()).stream().map(BroadcastResponse::from).toList();
    }

    // ---------- 등록/수정/삭제 (소유자) ----------

    @Transactional
    public BroadcastResponse create(AuthUser authUser, UpsertRequest req) {
        User owner = userRepository.findById(authUser.id())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        Broadcast saved = broadcastRepository.save(new Broadcast(owner, req.title().strip(), trimToNull(req.description())));
        return BroadcastResponse.from(saved);
    }

    @Transactional
    public BroadcastResponse update(AuthUser user, Long id, UpsertRequest req) {
        Broadcast b = findOwned(user, id);
        b.updateInfo(req.title().strip(), trimToNull(req.description()));
        return BroadcastResponse.from(b);
    }

    /** 방송 중이면 먼저 종료(송출 중단)한 뒤 삭제한다. */
    @Transactional
    public void delete(AuthUser user, Long id) {
        Broadcast b = findOwned(user, id);
        Instant now = clock.instant();
        if (b.isLive()) {
            endInternal(b, now);
        }
        b.softDelete(now);
    }

    // ---------- 송출 제어 (소유자) ----------

    @Transactional
    public ConnectionResponse start(AuthUser user, Long id, StartRequest req) {
        requireMediaServer();
        Broadcast b = findOwned(user, id);
        if (b.getStatus() != BroadcastStatus.READY) {
            throw new ApiException(ErrorCode.INVALID_BROADCAST_STATE);
        }
        String roomName = "bc-" + b.getId() + "-" + UUID.randomUUID().toString().substring(0, 8);
        mediaServer.createRoom(roomName);
        b.start(roomName, req.source(), clock.instant());
        recordingService.startFor(b);
        events.publishEvent(new BroadcastStartedEvent(b.getId(), b.getOwner().getId(), b.getTitle()));
        return publisherConnection(user, b);
    }

    /** 새로고침·네트워크 끊김 후 재접속용. 방송 중일 때만 발급한다. */
    @Transactional(readOnly = true)
    public ConnectionResponse publisherToken(AuthUser user, Long id) {
        requireMediaServer();
        Broadcast b = findOwned(user, id);
        if (!b.isLive()) {
            throw new ApiException(ErrorCode.BROADCAST_NOT_LIVE);
        }
        return publisherConnection(user, b);
    }

    @Transactional
    public BroadcastResponse end(AuthUser user, Long id) {
        Broadcast b = findOwned(user, id);
        endInternal(b, clock.instant());
        return BroadcastResponse.from(b);
    }

    // ---------- 시청 (비로그인 허용) ----------

    @Transactional(readOnly = true)
    public ConnectionResponse viewerToken(Long id, AuthUser userOrNull) {
        requireMediaServer();
        Broadcast b = find(id);
        if (!b.isLive()) {
            throw new ApiException(ErrorCode.BROADCAST_NOT_LIVE);
        }
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String identity = userOrNull == null ? "viewer-anon-" + suffix : "viewer-" + userOrNull.id() + "-" + suffix;
        String name = userOrNull == null ? "익명 시청자" : userOrNull.nickname();
        String token = mediaServer.issueViewerToken(b.getRoomName(), identity, name);
        return new ConnectionResponse(mediaServer.clientUrl(), token, b.getRoomName(), identity, "viewer");
    }

    // ---------- 미디어 서버 이벤트/스케줄러에서 호출 ----------

    @Transactional
    public void onPublisherJoined(String roomName, String identity) {
        broadcastRepository.findByRoomName(roomName)
                .filter(b -> publisherIdentity(b.getOwner().getId()).equals(identity))
                .ifPresent(Broadcast::markPublisherPresent);
    }

    @Transactional
    public void onPublisherLeft(String roomName, String identity) {
        Instant now = clock.instant();
        broadcastRepository.findByRoomName(roomName)
                .filter(b -> publisherIdentity(b.getOwner().getId()).equals(identity))
                .ifPresent(b -> b.markPublisherAbsent(now));
    }

    /** 미디어 서버에서 방이 사라졌는데 DB는 LIVE 라면 종료로 맞춘다. */
    @Transactional
    public void onRoomFinished(String roomName) {
        broadcastRepository.findByRoomName(roomName).filter(Broadcast::isLive).ifPresent(b -> {
            b.end(clock.instant());
            b.markRoomClosed();
            log.info("Broadcast {} ended because media room finished", b.getId());
        });
    }

    /** 송출자 접속 여부를 갱신하고, 유예 시간을 넘겨 부재하면 자동 종료한다. */
    @Transactional
    public void reconcilePresence(Long broadcastId, java.time.Duration grace) {
        Broadcast b = broadcastRepository.findActiveById(broadcastId).orElse(null);
        if (b == null || !b.isLive()) return;
        Instant now = clock.instant();
        boolean present = mediaServer.participantIdentities(b.getRoomName())
                .contains(publisherIdentity(b.getOwner().getId()));
        if (present) {
            b.markPublisherPresent();
        } else {
            b.markPublisherAbsent(now);
            if (b.publisherAbsentLongerThan(grace, now)) {
                log.info("Broadcast {} auto-ended: publisher absent since {}", b.getId(), b.getPublisherAbsentSince());
                endInternal(b, now);
            }
        }
    }

    /** 종료됐지만 방 정리에 실패한 방송을 다시 정리한다. */
    @Transactional
    public void retryCloseRoom(Long broadcastId) {
        broadcastRepository.findById(broadcastId)
                .filter(b -> b.getStatus() == BroadcastStatus.ENDED && !b.isRoomClosed())
                .ifPresent(this::closeRoom);
    }

    // ---------- 내부 ----------

    private void endInternal(Broadcast b, Instant now) {
        b.end(now);
        closeRoom(b);
    }

    private void closeRoom(Broadcast b) {
        try {
            mediaServer.deleteRoom(b.getRoomName());
            b.markRoomClosed();
        } catch (RuntimeException e) {
            // DB 상 종료는 유지하고 방 정리는 스케줄러가 재시도한다. 새 토큰은 발급되지 않는다.
            log.warn("Failed to delete room {} for broadcast {}, will retry", b.getRoomName(), b.getId(), e);
        }
    }

    private ConnectionResponse publisherConnection(AuthUser user, Broadcast b) {
        String identity = publisherIdentity(user.id());
        String token = mediaServer.issuePublisherToken(b.getRoomName(), identity, user.nickname());
        return new ConnectionResponse(mediaServer.clientUrl(), token, b.getRoomName(), identity, "publisher");
    }

    private Broadcast find(Long id) {
        return broadcastRepository.findActiveById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.BROADCAST_NOT_FOUND));
    }

    /** 존재 확인 → 소유자 확인 순서. 소유자가 아니면 403. */
    private Broadcast findOwned(AuthUser user, Long id) {
        if (user == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        Broadcast b = find(id);
        if (!b.isOwnedBy(user.id())) {
            throw new ApiException(ErrorCode.NOT_BROADCAST_OWNER);
        }
        return b;
    }

    private void requireMediaServer() {
        if (!mediaServer.isConfigured()) {
            throw new ApiException(ErrorCode.MEDIA_SERVER_NOT_CONFIGURED);
        }
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
