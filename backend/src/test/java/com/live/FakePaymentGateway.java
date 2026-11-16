package com.live;

import com.live.donation.PaymentGateway;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 테스트용 결제사. "결제창에서 결제된" 건을 등록해 두고 승인/조회에 응답한다. */
public class FakePaymentGateway implements PaymentGateway {
    /** paymentKey → 결제창에서 실제로 결제된 주문/금액 */
    public final Map<String, Payment> paid = new ConcurrentHashMap<>();
    public final AtomicInteger confirmCalls = new AtomicInteger();

    public void reset() { paid.clear(); confirmCalls.set(0); }

    public void userPaid(String paymentKey, String orderId, long amount) {
        paid.put(paymentKey, new Payment(paymentKey, orderId, "IN_PROGRESS", amount, null));
    }

    @Override public boolean isConfigured() { return true; }
    @Override public String clientKey() { return "test_ck_fake"; }

    @Override
    public Payment confirm(String paymentKey, String orderId, long amount) {
        confirmCalls.incrementAndGet();
        Payment p = paid.get(paymentKey);
        if (p == null || !p.orderId().equals(orderId)) throw new PaymentGatewayException("NOT_FOUND_PAYMENT", "없는 결제", true);
        if (p.totalAmount() != amount) throw new PaymentGatewayException("INVALID_AMOUNT", "금액 불일치", true);
        if (p.isDone()) throw new PaymentGatewayException("ALREADY_PROCESSED_PAYMENT", "이미 처리됨", true);
        Payment done = new Payment(paymentKey, orderId, "DONE", amount, Instant.now());
        paid.put(paymentKey, done);
        return done;
    }

    @Override
    public Payment find(String paymentKey) {
        Payment p = paid.get(paymentKey);
        if (p == null) throw new PaymentGatewayException("NOT_FOUND_PAYMENT", "없는 결제", true);
        return p;
    }

    /** 결제사 쪽에서 승인이 완료된 상태로 만든다(웹훅 시나리오). */
    public void approveDirectly(String paymentKey) {
        Payment p = paid.get(paymentKey);
        paid.put(paymentKey, new Payment(p.paymentKey(), p.orderId(), "DONE", p.totalAmount(), Instant.now()));
    }
}
