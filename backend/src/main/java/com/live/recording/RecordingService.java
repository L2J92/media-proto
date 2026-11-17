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

    /** 방송 시작 시 호출. 녹화가 실패해도 방송은 계속한다. */
    @Transactional
    public void startFor(Broadcast broadcast) {
        if (!properties.enabled()) return;
        String filePath = "recordings/broadcast-%d/%d/index.m3u8".formatted(broadcast.getId(), clock.millis());
        try {
            mediaServer.startRecording(broadcast.getRoomName(), filePath)
                    .ifPresent(egressId -> recordingRepository.save(new Recording(broadcast, egressId, filePath)));
        } catch (RuntimeException e) {
            log.warn("Recording start failed for broadcast {}", broadcast.getId(), e);
        }
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
        if (r == null || r.getStatus() != RecordingStatus.RECORDING || r.getBroadcast().isLive()) return;
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
