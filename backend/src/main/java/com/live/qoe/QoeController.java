package com.live.qoe;

import com.live.user.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.live.qoe.QoeDtos.*;

@RestController
public class QoeController {
    private final QoeService qoeService;

    public QoeController(QoeService qoeService) {
        this.qoeService = qoeService;
    }

    /** 시청자 플레이어의 품질 샘플 (비로그인 시청자 포함). 너무 잦으면 저장하지 않고 202. */
    @PostMapping("/api/broadcasts/{id}/qoe")
    public ResponseEntity<Void> report(@PathVariable Long id, @Valid @RequestBody SampleRequest req) {
        return qoeService.record(id, req) ? ResponseEntity.noContent().build() : ResponseEntity.accepted().build();
    }

    /** 방송 소유자 전용 품질 대시보드 데이터 */
    @GetMapping("/api/broadcasts/{id}/qoe")
    public Summary summary(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                           @RequestParam(defaultValue = "5") int minutes) {
        return qoeService.summary(user, id, minutes);
    }
}
