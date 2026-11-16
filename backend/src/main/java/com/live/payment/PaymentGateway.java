package com.live.payment;

import java.time.Instant;

/** 결제사 추상화. 실제 구현은 토스페이먼츠, 테스트는 가짜 구현. */
public interface PaymentGateway {

    boolean isConfigured();

    /** 브라우저 결제창 초기화에 쓰는 공개 키 */
    String clientKey();

    /** 결제 승인 요청. 결제사가 거절하면 PaymentGatewayException */
    Payment confirm(String paymentKey, String orderId, long amount);

    /** 결제 단건 조회 (웹훅 내용을 믿지 않고 다시 확인할 때) */
    Payment find(String paymentKey);

    record Payment(String paymentKey, String orderId, String status, long totalAmount, Instant approvedAt) {
        public boolean isDone() { return "DONE".equals(status); }
    }

    class PaymentGatewayException extends RuntimeException {
        private final String code;
        private final boolean clientError;

        public PaymentGatewayException(String code, String message, boolean clientError) {
            super(message);
            this.code = code;
            this.clientError = clientError;
        }

        public String code() { return code; }
        /** 결제사가 요청을 거절한 경우(true). 통신 장애는 false */
        public boolean clientError() { return clientError; }
    }
}
