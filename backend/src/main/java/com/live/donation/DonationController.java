package com.live.donation;

import com.live.user.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.live.donation.DonationDtos.*;

@RestController
public class DonationController {
    private final DonationService donationService;

    public DonationController(DonationService donationService) {
        this.donationService = donationService;
    }

    /** 포인트로 후원 (잔액 부족 시 409 INSUFFICIENT_POINTS → 화면에서 "충전하고 바로 후원") */
    @PostMapping("/api/broadcasts/{broadcastId}/donations")
    public DonateResult donate(@AuthenticationPrincipal AuthUser user, @PathVariable Long broadcastId,
                               @Valid @RequestBody DonateRequest req) {
        return donationService.donate(user, broadcastId, req);
    }

    @GetMapping("/api/broadcasts/{broadcastId}/donations")
    public DonationSummary summary(@PathVariable Long broadcastId) {
        return donationService.summary(broadcastId);
    }
}
