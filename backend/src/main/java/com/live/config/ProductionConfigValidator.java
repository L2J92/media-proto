package com.live.config;

import com.live.media.LiveKitProperties;
import com.live.payment.TossPaymentsProperties;
import com.live.recording.RecordingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 기동 시 설정 점검.
 * REQUIRE_PRODUCTION_CONFIG=true(운영)이면 필수 설정이 빠졌을 때 기동을 실패시켜, "뜨긴 했는데 방송·데이터가 안 되는"
 * 상태가 배포되지 않게 한다. (Render 는 새 배포가 뜨지 않으면 이전 버전을 계속 서비스한다.)
 */
@Component
public class ProductionConfigValidator {
    private static final Logger log = LoggerFactory.getLogger(ProductionConfigValidator.class);

    private final boolean required;
    private final String dbUrl;
    private final LiveKitProperties livekit;
    private final TossPaymentsProperties toss;
    private final RecordingProperties recording;

    public ProductionConfigValidator(@Value("${app.require-production-config:false}") boolean required,
                                     @Value("${spring.datasource.url:}") String dbUrl,
                                     LiveKitProperties livekit, TossPaymentsProperties toss, RecordingProperties recording) {
        this.required = required;
        this.dbUrl = dbUrl;
        this.livekit = livekit;
        this.toss = toss;
        this.recording = recording;
    }

    /** 필수 설정 중 빠진 환경변수 이름 목록 */
    public List<String> missingRequired() {
        List<String> missing = new ArrayList<>();
        if (dbUrl == null || !dbUrl.startsWith("jdbc:postgresql:")) missing.add("DB_URL (jdbc:postgresql://…)");
        if (!livekit.configured()) missing.add("LIVEKIT_URL / LIVEKIT_API_KEY / LIVEKIT_API_SECRET");
        return missing;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void validate() {
        boolean recordingReady = recording.enabled() && recording.s3() != null
                && notBlank(recording.s3().bucket()) && notBlank(recording.s3().publicBaseUrl());
        log.info("Config summary: db={}, livekit={}, payments={}, recording={}",
                dbUrl == null ? "-" : dbUrl.replaceFirst("^(jdbc:[a-z0-9]+):.*$", "$1"),
                livekit.configured() ? "on" : "OFF",
                toss.configured() ? "on" : (toss.clientKeyLooksSecret() ? "OFF (client key looks like a secret key)" : "OFF"),
                recordingReady ? "on" : (recording.enabled() ? "OFF (S3_BUCKET / S3_PUBLIC_BASE_URL missing)" : "off"));

        List<String> missing = missingRequired();
        if (required && !missing.isEmpty()) {
            throw new IllegalStateException("운영 필수 환경변수가 없습니다: " + String.join(", ", missing)
                    + " — Render → Environment 에서 입력하세요. (점검을 끄려면 REQUIRE_PRODUCTION_CONFIG=false)");
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
