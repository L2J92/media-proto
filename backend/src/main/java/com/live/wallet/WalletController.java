package com.live.wallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.live.common.ApiException;
import com.live.common.ErrorCode;
import com.live.user.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.live.wallet.WalletDtos.*;

@RestController
public class WalletController {
    private final WalletService walletService;
    private final ChargeService chargeService;

    public WalletController(WalletService walletService, ChargeService chargeService) {
        this.walletService = walletService;
        this.chargeService = chargeService;
    }

    @GetMapping("/api/wallet")
    public WalletService.WalletResponse wallet(@AuthenticationPrincipal AuthUser user) {
        if (user == null) throw new ApiException(ErrorCode.UNAUTHORIZED);
        return walletService.get(user.id());
    }

    @PostMapping("/api/wallet/charges")
    public ChargePrepareResponse prepare(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ChargeRequest req) {
        return chargeService.prepare(user, req);
    }

    @PostMapping("/api/wallet/charges/confirm")
    public ChargeResult confirm(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ConfirmRequest req) {
        return chargeService.confirm(user, req);
    }

    /** 토스페이먼츠 웹훅 (PAYMENT_STATUS_CHANGED). 같은 통지가 여러 번 와도 한 번만 충전된다. */
    @PostMapping("/api/webhooks/toss")
    public ResponseEntity<Void> webhook(@RequestBody JsonNode body) {
        JsonNode data = body.path("data");
        chargeService.onPaymentNotification(data.path("paymentKey").asText(null), data.path("orderId").asText(null));
        return ResponseEntity.ok().build();
    }
}
