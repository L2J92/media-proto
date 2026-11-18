package com.live.recording;

import com.live.user.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class RecordingController {
    private final RecordingService recordingService;
    private final com.live.broadcast.BroadcastRepository broadcastRepository;

    public RecordingController(RecordingService recordingService, com.live.broadcast.BroadcastRepository broadcastRepository) {
        this.recordingService = recordingService;
        this.broadcastRepository = broadcastRepository;
    }

    /** 방송자: 방송 중 녹화 시작 */
    @PostMapping("/api/broadcasts/{broadcastId}/recordings/start")
    public RecordingResponse start(@PathVariable Long broadcastId, @AuthenticationPrincipal AuthUser user) {
        return recordingService.start(user == null ? null : user.id(), broadcast(broadcastId));
    }

    /** 방송자: 녹화 중지 → 저장이 끝나면 다시보기에 올라감 */
    @PostMapping("/api/broadcasts/{broadcastId}/recordings/stop")
    public RecordingResponse stop(@PathVariable Long broadcastId, @AuthenticationPrincipal AuthUser user) {
        return recordingService.stop(user == null ? null : user.id(), broadcast(broadcastId));
    }

    private com.live.broadcast.Broadcast broadcast(Long id) {
        return broadcastRepository.findActiveById(id)
                .orElseThrow(() -> new com.live.common.ApiException(com.live.common.ErrorCode.BROADCAST_NOT_FOUND));
    }

    @GetMapping("/api/recordings")
    public List<RecordingResponse> list() {
        return recordingService.list();
    }

    @GetMapping("/api/recordings/{id}")
    public RecordingResponse get(@PathVariable Long id) {
        return recordingService.get(id);
    }

    @GetMapping("/api/broadcasts/{broadcastId}/recordings")
    public List<RecordingResponse> byBroadcast(@PathVariable Long broadcastId, @AuthenticationPrincipal AuthUser user) {
        return recordingService.listByBroadcast(broadcastId, user == null ? null : user.id());
    }
}
