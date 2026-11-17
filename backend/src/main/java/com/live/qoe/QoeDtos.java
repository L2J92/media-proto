package com.live.qoe;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class QoeDtos {
    private QoeDtos() {}

    /** 시청자 플레이어가 보내는 구간 측정값. 값 범위를 넘으면 400 (잘못된/조작된 값 차단). */
    public record SampleRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "viewer-[A-Za-z0-9_-]+") String viewerKey,
            @Min(0) @Max(100_000) int bitrateKbps,
            @DecimalMin("0") @DecimalMax("100") double packetLossPct,
            @DecimalMin("0") @DecimalMax("10000") double jitterMs,
            @DecimalMin("0") @DecimalMax("10000") double rttMs,
            @DecimalMin("0") @DecimalMax("240") double fps,
            @Min(0) @Max(100_000) int framesDropped,
            @Min(0) @Max(10_000) int freezeCount,
            @Min(0) @Max(4320) int frameHeight,
            @NotBlank @Pattern(regexp = "auto|high|medium|low") String quality) {}

    /** 분 단위 추이 한 칸 */
    public record Bucket(Instant at, int viewers, double avgBitrateKbps, double avgPacketLossPct, double avgRttMs, int freezes) {}

    public record Summary(
            int windowMinutes,
            int reportingViewers,
            long samples,
            double avgBitrateKbps,
            double avgRttMs,
            double p95RttMs,
            double avgPacketLossPct,
            double avgJitterMs,
            double avgFps,
            int totalFreezes,
            int totalFramesDropped,
            /** 화질별 비율(%) — 시청자가 어떤 레이어를 보고 있는지 */
            java.util.Map<String, Double> qualityShare,
            /** 가장 많이 받은 세로 해상도 */
            int typicalHeight,
            List<Bucket> timeline) {}
}
