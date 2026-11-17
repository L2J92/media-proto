package com.live.recording;

import com.live.user.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class RecordingController {
    private final RecordingService recordingService;

    public RecordingController(RecordingService recordingService) {
        this.recordingService = recordingService;
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
