package com.live.qoe;

import com.live.broadcast.Broadcast;
import com.live.broadcast.BroadcastRepository;
import com.live.common.ApiException;
import com.live.common.ErrorCode;
import com.live.user.AuthUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.live.qoe.QoeDtos.*;

@Service
public class QoeService {
    /** 시청자 한 명이 보낼 수 있는 최소 간격 (플레이어는 10초마다 보냄) */
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(5);
    private static final int MAX_SAMPLES = 5000;

    private final QoeSampleRepository repository;
    private final BroadcastRepository broadcastRepository;
    private final Clock clock;
    private final Map<String, Instant> lastReport = new ConcurrentHashMap<>();

    public QoeService(QoeSampleRepository repository, BroadcastRepository broadcastRepository, Clock clock) {
        this.repository = repository;
        this.broadcastRepository = broadcastRepository;
        this.clock = clock;
    }

    /** @return 저장했으면 true, 너무 잦은 보고라 버렸으면 false */
    @Transactional
    public boolean record(Long broadcastId, SampleRequest req) {
        Broadcast b = broadcastRepository.findActiveById(broadcastId)
                .orElseThrow(() -> new ApiException(ErrorCode.BROADCAST_NOT_FOUND));
        if (!b.isLive()) throw new ApiException(ErrorCode.BROADCAST_NOT_LIVE);
        Instant now = clock.instant();
        String key = broadcastId + ":" + req.viewerKey();
        Instant prev = lastReport.get(key);
        if (prev != null && prev.plus(MIN_INTERVAL).isAfter(now)) return false;
        lastReport.put(key, now);
        repository.save(new QoeSample(b, req.viewerKey(), req, now));
        return true;
    }

    /** 방송 소유자용 품질 요약 */
    @Transactional(readOnly = true)
    public Summary summary(AuthUser user, Long broadcastId, int minutes) {
        Broadcast b = broadcastRepository.findActiveById(broadcastId)
                .orElseThrow(() -> new ApiException(ErrorCode.BROADCAST_NOT_FOUND));
        if (user == null) throw new ApiException(ErrorCode.UNAUTHORIZED);
        if (!b.isOwnedBy(user.id())) throw new ApiException(ErrorCode.NOT_BROADCAST_OWNER);
        int window = Math.clamp(minutes, 1, 180);
        Instant since = clock.instant().minus(Duration.ofMinutes(window));
        List<QoeSample> samples = repository.findSince(broadcastId, since, PageRequest.of(0, MAX_SAMPLES));
        return summarize(window, samples);
    }

    static Summary summarize(int window, List<QoeSample> s) {
        if (s.isEmpty()) {
            return new Summary(window, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of(), 0, List.of());
        }
        double[] rtts = s.stream().mapToDouble(QoeSample::getRttMs).sorted().toArray();
        double p95 = rtts[Math.min(rtts.length - 1, (int) Math.ceil(rtts.length * 0.95) - 1)];
        Map<String, Double> share = s.stream().collect(Collectors.groupingBy(QoeSample::getQuality, TreeMap::new,
                Collectors.collectingAndThen(Collectors.counting(), c -> round1(c * 100.0 / s.size()))));
        int typicalHeight = s.stream().collect(Collectors.groupingBy(QoeSample::getFrameHeight, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0);

        TreeMap<Instant, List<QoeSample>> byMinute = s.stream().collect(Collectors.groupingBy(
                q -> q.getCreatedAt().truncatedTo(ChronoUnit.MINUTES), TreeMap::new, Collectors.toList()));
        List<Bucket> timeline = byMinute.entrySet().stream().map(e -> {
            List<QoeSample> m = e.getValue();
            return new Bucket(e.getKey(),
                    (int) m.stream().map(QoeSample::getViewerKey).distinct().count(),
                    round1(m.stream().mapToInt(QoeSample::getBitrateKbps).average().orElse(0)),
                    round2(m.stream().mapToDouble(QoeSample::getPacketLossPct).average().orElse(0)),
                    round1(m.stream().mapToDouble(QoeSample::getRttMs).average().orElse(0)),
                    m.stream().mapToInt(QoeSample::getFreezeCount).sum());
        }).toList();

        return new Summary(window,
                (int) s.stream().map(QoeSample::getViewerKey).distinct().count(),
                s.size(),
                round1(s.stream().mapToInt(QoeSample::getBitrateKbps).average().orElse(0)),
                round1(Arrays.stream(rtts).average().orElse(0)),
                round1(p95),
                round2(s.stream().mapToDouble(QoeSample::getPacketLossPct).average().orElse(0)),
                round1(s.stream().mapToDouble(QoeSample::getJitterMs).average().orElse(0)),
                round1(s.stream().mapToDouble(QoeSample::getFps).average().orElse(0)),
                s.stream().mapToInt(QoeSample::getFreezeCount).sum(),
                s.stream().mapToInt(QoeSample::getFramesDropped).sum(),
                share, typicalHeight, timeline);
    }

    /** 오래된 샘플 정리 (7일 보관) */
    @Scheduled(cron = "0 17 4 * * *")
    @Transactional
    public void purgeOld() {
        repository.deleteOlderThan(clock.instant().minus(Duration.ofDays(7)));
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(10));
        lastReport.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
    }

    private static double round1(double v) { return Math.round(v * 10) / 10.0; }
    private static double round2(double v) { return Math.round(v * 100) / 100.0; }
}
