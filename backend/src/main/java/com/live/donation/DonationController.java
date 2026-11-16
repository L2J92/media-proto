package com.live.donation;

import com.fasterxml.jackson.databind.JsonNode;
import com.live.user.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.live.donation.DonationDtos.*;

@RestController
public class DonationController {
    private final DonationService donationService;

    public DonationController(DonationService donationService) {
        this.donationService = donationService;
    }

    @PostMapping("/api/broadcasts/{broadcastId}/donations")
    public PrepareResponse prepare(@AuthenticationPrincipal AuthUser user, @PathVariable Long broadcastId,
                                   @Valid @RequestBody PrepareRequest req) {
        return donationService.prepare(user, broadcastId, req);
    }

    @PostMapping("/api/donations/confirm")
    public DonationResponse confirm(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ConfirmRequest req) {
        return donationService.confirm(user, req);
    }

    @GetMapping("/api/broadcasts/{broadcastId}/donations")
    public DonationSummary summary(@PathVariable Long broadcastId) {
        return donationService.summary(broadcastId);
    }

    /** 토스페이먼츠 웹훅 (PAYMENT_STATUS_CHANGED). 같은 통지가 여러 번 와도 한 번만 적립된다. */
    @PostMapping("/api/webhooks/toss")
    public ResponseEntity<Void> webhook(@RequestBody JsonNode body) {
        JsonNode data = body.path("data");
        donationService.onPaymentNotification(data.path("paymentKey").asText(null), data.path("orderId").asText(null));
        return ResponseEntity.ok().build();
    }
}
