package com.live.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "toss")
public record TossPaymentsProperties(String clientKey, String secretKey, String apiUrl) {

    public boolean configured() {
        return notBlank(clientKey) && notBlank(secretKey) && !clientKeyLooksSecret();
    }

    /**
     * 클라이언트 키는 브라우저로 내려가므로, 시크릿 키(…_sk_, …_gsk_)가 잘못 들어가 있으면 결제를 막는다.
     */
    public boolean clientKeyLooksSecret() {
        return notBlank(clientKey) && (clientKey.contains("_sk_") || clientKey.contains("_gsk_"));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
