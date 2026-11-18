package com.live.recording;

import com.live.broadcast.Broadcast;
import com.live.common.ApiException;
import com.live.common.ErrorCode;
import com.live.media.MediaServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class RecordingService {
    private static final Logger log = LoggerFactory.getLogger(RecordingService.class);

    private final RecordingRepository recordingRepository;
    private final MediaServer mediaServer;
    private final RecordingProperties properties;
    private final Clock clock;

    public RecordingService(RecordingRepository recordingRepository, MediaServer mediaServer,
                            RecordingProperties properties, Clock clock) {
        this.recordingRepository = recordingRepository;
        this.mediaServer = mediaServer;
        this.properties = properties;
        this.clock = clock;
    }

    public boolean available() {
        return properties.enabled() && properties.s3() != null
                && properties.s3().bucket() != null && !properties.s3().bucket().isBlank();
    }

    /** 방송 시작 시 자동 녹화. 실패해도 방송은 계속한다. */
    @Transactional
    public void startFor(Broadcast broadcast) {
        if (!available()) return;
        try {
            begin(broadcast);
        } catch (RuntimeException e) {
            log.warn("Recording start failed for broadcast {}", broadcast.getId(), e);
        }
    }

    /** 방송자가 방송 중에 녹화를 시작한다 (동시에 하나만) */
    @Transactional
    public RecordingResponse start(Long ownerId, Broadcast broadcast) {
        requireOwnerLive(ownerId, broadcast);
        if (!available()) throw new ApiException(ErrorCode.RECORDING_NOT_AVAILABLE);
        if (!recordingRepository.findActive(broadcast.getId()).isEmpty()) throw new ApiException(ErrorCode.RECORDING_ALREADY_ACTIVE);
        return toResponse(begin(broadcast).orElseThrow(() -> new ApiException(ErrorCode.RECORDING_NOT_AVAILABLE)));
    }

    /** 방송자가 녹화를 멈춘다. 저장이 끝나면(약 수십 초) 다시보기에 올라간다 */
    @Transactional
    public RecordingResponse stop(Long ownerId, Broadcast broadcast) {
        requireOwnerLive(ownerId, broadcast);
        Recording r = recordingRepository.findActive(broadcast.getId()).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.RECORDING_NOT_ACTIVE));
        mediaServer.stopRecording(r.getEgressId());
        r.requestStop();
        return toResponse(r);
    }

    private java.util.Optional<Recording> begin(Broadcast broadcast) {
        String filePath = "recordings/broadcast-%d/%d/index.m3u8".formatted(broadcast.getId(), clock.millis());
        return mediaServer.startRecording(broadcast.getRoomName(), filePath)
                .map(egressId -> recordingRepository.save(new Recording(broadcast, egressId, filePath)));
    }

    private static void requireOwnerLive(Long ownerId, Broadcast b) {
        if (ownerId == null) throw new ApiException(ErrorCode.UNAUTHORIZED);
        if (!b.isOwnedBy(ownerId)) throw new ApiException(ErrorCode.NOT_BROADCAST_OWNER);
        if (!b.isLive()) throw new ApiException(ErrorCode.BROADCAST_NOT_LIVE);
    }

    @Transactional
    public void onEgressEnded(String egressId, boolean succeeded, String location, long durationMillis, String error) {
        recordingRepository.findByEgressId(egressId).ifPresent(r -> {
            if (succeeded) r.complete(location, durationMillis);
            else fail(r, error);
        });
    }

    /**
     * 웹훅이 오지 않아도(미설정·유실) 녹화가 "처리 중"에 머물지 않도록 미디어 서버에 직접 상태를 물어 확정한다.
     * Recording.complete/fail 은 한 번만 바뀌므로 웹훅과 겹쳐도 안전하다.
     */
    @Transactional
    public void reconcile(Long recordingId) {
        Recording r = recordingRepository.findById(recordingId).orElse(null);
        // 방송이 끝났거나, 방송 중이라도 방송자가 중지한 녹화만 확정한다
        if (r == null || r.getStatus() != RecordingStatus.RECORDING || (r.getBroadcast().isLive() && !r.isStopRequested())) return;
        mediaServer.findRecording(r.getEgressId()).filter(MediaServer.RecordingState::finished).ifPresent(st -> {
            if (st.succeeded()) r.complete(st.location(), st.durationMillis());
            else fail(r, st.error());
        });
    }

    private void fail(Recording r, String error) {
        log.warn("Recording {} (egress {}) failed: {}", r.getId(), r.getEgressId(), error);
        r.fail(error);
    }

    @Transactional(readOnly = true)
    public List<Long> pendingRecordingIds() {
        return recordingRepository.findByStatus(RecordingStatus.RECORDING).stream().map(Recording::getId).toList();
    }

    @Transactional(readOnly = true)
    public List<RecordingResponse> list() {
        return recordingRepository.findVisible(RecordingStatus.COMPLETED).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RecordingResponse> listByBroadcast(Long broadcastId, Long viewerId) {
        // 실패 사유는 방송 소유자에게만 보여준다 (저장소 설정 정보가 섞일 수 있음)
        return recordingRepository.findByBroadcast(broadcastId).stream().map(r -> {
            RecordingResponse res = toResponse(r);
            boolean owner = viewerId != null && r.getBroadcast().isOwnedBy(viewerId);
            return owner ? res.withFailReason(r.getFailReason()) : res;
        }).toList();
    }

    @Transactional(readOnly = true)
    public RecordingResponse get(Long id) {
        return recordingRepository.findVisibleById(id).map(this::toResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.RECORDING_NOT_FOUND));
    }

    private RecordingResponse toResponse(Recording r) {
        String url = r.getStatus() == RecordingStatus.COMPLETED ? properties.publicUrl(r.getFilePath()) : null;
        return RecordingResponse.from(r, url);
    }
}
