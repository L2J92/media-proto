package com.live.donation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class DonationDtos {
    private DonationDtos() {}

    public record PrepareRequest(
            @Min(value = 1000, message = "1,000원부터 후원할 수 있습니다.")
            @Max(value = 1_000_000, message = "한 번에 1,000,000원까지 후원할 수 있습니다.") long amount,
            @Size(max = 100) String message) {}

    /** 결제창 호출에 필요한 정보. 금액·주문번호는 서버가 정한 값만 쓴다. */
    public record PrepareResponse(String orderId, long amount, String orderName, String clientKey,
                                  String customerKey, String customerName) {}

    /** 결제창 성공 리다이렉트로 받은 값. 서버가 주문과 대조한 뒤 결제사에 승인 요청한다. */
    public record ConfirmRequest(@NotBlank String paymentKey, @NotBlank String orderId, long amount) {}

    public record DonationResponse(Long id, Long broadcastId, String donorNickname, String streamerNickname,
                                   long amount, String message, Instant approvedAt) {
        public static DonationResponse from(Donation d) {
            return new DonationResponse(d.getId(), d.getBroadcast().getId(), d.getDonor().getNickname(),
                    d.getStreamer().getNickname(), d.getAmount(), d.getMessage(), d.getApprovedAt());
        }
    }

    public record DonationSummary(long totalAmount, long count, List<DonationResponse> recent) {}
}
