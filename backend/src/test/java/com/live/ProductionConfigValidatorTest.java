package com.live;

import com.live.config.ProductionConfigValidator;
import com.live.media.LiveKitProperties;
import com.live.payment.TossPaymentsProperties;
import com.live.recording.RecordingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigValidatorTest {
    private static final LiveKitProperties LIVEKIT = new LiveKitProperties("wss://x.livekit.cloud", "key", "secret");
    private static final LiveKitProperties NO_LIVEKIT = new LiveKitProperties("", "", "");
    private static final TossPaymentsProperties TOSS = new TossPaymentsProperties(null, null, null);
    private static final RecordingProperties REC = new RecordingProperties(false, null);

    @Test
    void failsInProductionWhenDbIsNotPostgresOrLiveKitMissing() {
        var v = new ProductionConfigValidator(true, "jdbc:h2:file:./data/live", NO_LIVEKIT, TOSS, REC);
        assertThat(v.missingRequired()).hasSize(2);
        assertThatThrownBy(v::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL").hasMessageContaining("LIVEKIT_URL");
    }

    @Test
    void passesInProductionWhenRequiredConfigPresent() {
        var v = new ProductionConfigValidator(true, "jdbc:postgresql://db/neondb?sslmode=require", LIVEKIT, TOSS, REC);
        assertThat(v.missingRequired()).isEmpty();
        v.validate();
    }

    @Test
    void onlyWarnsLocally() {
        new ProductionConfigValidator(false, "jdbc:h2:mem:x", NO_LIVEKIT, TOSS, REC).validate();
    }
}
