package com.live.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "livekit")
public record LiveKitProperties(String url, String apiKey, String apiSecret) {
    public boolean configured() {
        return notBlank(url) && notBlank(apiKey) && notBlank(apiSecret);
    }

    /** 서버 API 호출용 https URL (wss:// → https://) */
    public String httpUrl() {
        return url.replaceFirst("^wss://", "https://").replaceFirst("^ws://", "http://");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
