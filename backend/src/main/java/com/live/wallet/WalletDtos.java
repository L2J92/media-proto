package com.live.wallet;

import com.live.donation.DonationDtos.DonationResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class WalletDtos {
    private WalletDtos() {}

    /** 충전 요청. donation 이 있으면 충전 후 그 방송에 같은 금액을 바로 후원한다. */
    public record ChargeRequest(
            @Min(value = 1000, message = "1,000원부터 충전할 수 있습니다.")
            @Max(value = 1_000_000, message = "한 번에 1,000,000원까지 충전할 수 있습니다.") long amount,
            @Valid DonationTarget donation) {}

    public record DonationTarget(@NotNull Long broadcastId, @Size(max = 100) String message) {}

    /** 결제창 호출에 필요한 정보. 금액·주문번호는 서버가 정한 값만 쓴다. */
    public record ChargePrepareResponse(String orderId, long amount, String orderName, String clientKey,
                                        String customerKey, String customerName) {}

    /** 결제창 성공 리다이렉트로 받은 값. 서버가 주문과 대조한 뒤 결제사에 승인 요청한다. */
    public record ConfirmRequest(@NotBlank String paymentKey, @NotBlank String orderId, long amount) {}

    /** 충전 결과. 바로 후원이었다면 donation 이 채워진다. */
    public record ChargeResult(String orderId, long chargedAmount, long balance, DonationResponse donation) {}
}
