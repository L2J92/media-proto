package com.live.donation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "toss")
public record TossPaymentsProperties(String clientKey, String secretKey, String apiUrl) {
    public boolean configured() {
        return clientKey != null && !clientKey.isBlank() && secretKey != null && !secretKey.isBlank();
    }
}
