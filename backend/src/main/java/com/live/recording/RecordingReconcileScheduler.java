package com.live.recording;

import com.live.media.MediaServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 처리 중인 녹화를 주기적으로 미디어 서버와 맞춘다(웹훅 유실 대비). */
@Component
public class RecordingReconcileScheduler {
    private static final Logger log = LoggerFactory.getLogger(RecordingReconcileScheduler.class);

    private final RecordingService recordingService;
    private final MediaServer mediaServer;

    public RecordingReconcileScheduler(RecordingService recordingService, MediaServer mediaServer) {
        this.recordingService = recordingService;
        this.mediaServer = mediaServer;
    }

    @Scheduled(fixedDelayString = "${recording.reconcile-interval:30s}", initialDelayString = "20s")
    public void reconcile() {
        if (!mediaServer.isConfigured()) return;
        for (Long id : recordingService.pendingRecordingIds()) {
            try {
                recordingService.reconcile(id);
            } catch (RuntimeException e) {
                log.warn("Recording reconcile failed for {}", id, e);
            }
        }
    }
}
