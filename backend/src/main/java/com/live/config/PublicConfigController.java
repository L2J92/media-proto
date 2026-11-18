package com.live.config;

import com.live.payment.PaymentGateway;
import com.live.recording.RecordingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 화면에서 기능 버튼 노출을 정하기 위한 공개 설정 (비밀 값은 담지 않는다) */
@RestController
public class PublicConfigController {
    private final RecordingService recordingService;
    private final PaymentGateway paymentGateway;
    private final boolean demoAccounts;

    public PublicConfigController(RecordingService recordingService, PaymentGateway paymentGateway,
                                  @Value("${app.demo-accounts-enabled}") boolean demoAccounts) {
        this.recordingService = recordingService;
        this.paymentGateway = paymentGateway;
        this.demoAccounts = demoAccounts;
    }

    @GetMapping("/api/config")
    public PublicConfig config() {
        return new PublicConfig(recordingService.available(), paymentGateway.isConfigured(), demoAccounts);
    }

    public record PublicConfig(boolean recordingEnabled, boolean paymentsEnabled, boolean demoAccountsEnabled) {}
}
