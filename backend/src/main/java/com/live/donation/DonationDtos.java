package com.live.donation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class DonationDtos {
    private DonationDtos() {}

    /** 포인트 후원 요청. requestId 는 클라이언트가 만든 고유값(중복 클릭 방지). */
    public record DonateRequest(
            @Min(value = 1000, message = "1,000P부터 후원할 수 있습니다.")
            @Max(value = 1_000_000, message = "한 번에 1,000,000P까지 후원할 수 있습니다.") long amount,
            @Size(max = 100) String message,
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String requestId) {}

    public record DonationResponse(Long id, Long broadcastId, String donorNickname, String streamerNickname,
                                   long amount, String message, Instant createdAt) {
        public static DonationResponse from(Donation d) {
            return new DonationResponse(d.getId(), d.getBroadcast().getId(), d.getDonor().getNickname(),
                    d.getStreamer().getNickname(), d.getAmount(), d.getMessage(), d.getCreatedAt());
        }
    }

    /** 후원 결과와 보낸 사람의 남은 포인트 */
    public record DonateResult(DonationResponse donation, long balance) {}

    public record DonationSummary(long totalAmount, long count, List<DonationResponse> recent) {}
}
