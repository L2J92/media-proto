package com.live.donation;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;

/** 토스페이먼츠 코어 API (결제 승인 / 조회) */
@Component
public class TossPaymentsGateway implements PaymentGateway {
    private final TossPaymentsProperties props;
    private final RestClient client;

    public TossPaymentsGateway(TossPaymentsProperties props, RestClient.Builder builder) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        String secret = props.secretKey() == null ? "" : props.secretKey();
        this.client = builder
                .baseUrl(props.apiUrl() == null || props.apiUrl().isBlank() ? "https://api.tosspayments.com" : props.apiUrl())
                .requestFactory(factory)
                // 시크릿 키 뒤에 콜론을 붙여 Base64 로 인코딩한 Basic 인증
                .defaultHeader("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString((secret + ":").getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    @Override
    public boolean isConfigured() {
        return props.configured();
    }

    @Override
    public String clientKey() {
        return props.clientKey();
    }

    @Override
    public Payment confirm(String paymentKey, String orderId, long amount) {
        return call(() -> client.post().uri("/v1/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                // 같은 주문의 승인 요청이 재시도돼도 결제사에서 한 번만 처리되게 한다.
                .header("Idempotency-Key", "confirm-" + orderId)
                .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
                .retrieve().body(JsonNode.class));
    }

    @Override
    public Payment find(String paymentKey) {
        return call(() -> client.get().uri("/v1/payments/{paymentKey}", paymentKey)
                .retrieve().body(JsonNode.class));
    }

    private Payment call(java.util.function.Supplier<JsonNode> request) {
        try {
            return toPayment(request.get());
        } catch (RestClientResponseException e) {
            JsonNode err = null;
            try {
                err = e.getResponseBodyAs(JsonNode.class);
            } catch (RuntimeException ignored) {
                // 본문이 JSON 이 아니면 상태 코드만 사용
            }
            String code = err != null && err.hasNonNull("code") ? err.get("code").asText() : "HTTP_" + e.getStatusCode().value();
            String message = err != null && err.hasNonNull("message") ? err.get("message").asText() : e.getMessage();
            HttpStatusCode status = e.getStatusCode();
            throw new PaymentGatewayException(code, message, status.is4xxClientError());
        } catch (ResourceAccessException e) {
            throw new PaymentGatewayException("NETWORK_ERROR", e.getMessage(), false);
        }
    }

    private static Payment toPayment(JsonNode n) {
        if (n == null) throw new PaymentGatewayException("EMPTY_RESPONSE", "empty response", false);
        Instant approvedAt = n.hasNonNull("approvedAt") ? OffsetDateTime.parse(n.get("approvedAt").asText()).toInstant() : null;
        return new Payment(n.path("paymentKey").asText(null), n.path("orderId").asText(null),
                n.path("status").asText(null), n.path("totalAmount").asLong(), approvedAt);
    }
}
