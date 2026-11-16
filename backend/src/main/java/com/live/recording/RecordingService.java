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
        String filePath = "recordings/broadcast-%d/%d.mp4".formatted(broadcast.getId(), clock.millis());
        try {
            mediaServer.startRecording(broadcast.getRoomName(), filePath)
                    .ifPresent(egressId -> recordingRepository.save(new Recording(broadcast, egressId, filePath)));
        } catch (RuntimeException e) {
            log.warn("Recording start failed for broadcast {}", broadcast.getId(), e);
        }
    }

    @Transactional
    public void onEgressEnded(String egressId, boolean succeeded, String location, long durationMillis) {
        recordingRepository.findByEgressId(egressId).ifPresent(r -> {
            if (succeeded) r.complete(location, durationMillis);
            else r.fail();
        });
    }

    @Transactional(readOnly = true)
    public List<RecordingResponse> list() {
        return recordingRepository.findVisible(RecordingStatus.COMPLETED).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RecordingResponse> listByBroadcast(Long broadcastId) {
        return recordingRepository.findByBroadcast(broadcastId).stream().map(this::toResponse).toList();
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
