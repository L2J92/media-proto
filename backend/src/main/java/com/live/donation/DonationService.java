package com.live.donation;

import com.live.broadcast.Broadcast;
import com.live.broadcast.BroadcastRepository;
import com.live.common.ApiException;
import com.live.common.ErrorCode;
import com.live.user.AuthUser;
import com.live.user.User;
import com.live.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

import static com.live.donation.DonationDtos.*;

/**
 * 후원 흐름
 * 1) prepare: 서버가 주문(orderId, 금액, 후원 대상)을 먼저 저장한다.
 * 2) 브라우저가 토스 결제창에서 결제한다.
 * 3) confirm: 서버가 주문과 금액을 대조한 뒤 결제사에 승인 요청 → 응답의 상태·금액·주문번호를 다시 검증 → 적립.
 * 4) webhook: 결제사 통지가 와도 내용을 믿지 않고 결제사에 다시 조회해 같은 방식으로 확정한다.
 * 적립은 주문 행을 잠근 상태에서 PENDING → DONE 으로 한 번만 바뀌므로, 같은 통지가 반복돼도 한 번만 반영된다.
 */
@Service
public class DonationService {
    private static final Logger log = LoggerFactory.getLogger(DonationService.class);

    private final DonationRepository donationRepository;
    private final BroadcastRepository broadcastRepository;
    private final UserRepository userRepository;
    private final PaymentGateway gateway;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DonationService(DonationRepository donationRepository, BroadcastRepository broadcastRepository,
                           UserRepository userRepository, PaymentGateway gateway,
                           ApplicationEventPublisher events, Clock clock) {
        this.donationRepository = donationRepository;
        this.broadcastRepository = broadcastRepository;
        this.userRepository = userRepository;
        this.gateway = gateway;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public PrepareResponse prepare(AuthUser user, Long broadcastId, PrepareRequest req) {
        requireGateway();
        Broadcast b = broadcastRepository.findActiveById(broadcastId)
                .orElseThrow(() -> new ApiException(ErrorCode.BROADCAST_NOT_FOUND));
        if (b.isOwnedBy(user.id())) {
            throw new ApiException(ErrorCode.CANNOT_DONATE_TO_SELF);
        }
        User donor = userRepository.findById(user.id()).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        String orderId = "don_" + UUID.randomUUID().toString().replace("-", "");
        String customerKey = "cust_" + UUID.randomUUID();
        String message = req.message() == null || req.message().isBlank() ? null : req.message().strip();
        Donation d = donationRepository.save(new Donation(b, donor, req.amount(), message, orderId, customerKey));
        String orderName = b.getOwner().getNickname() + "님 후원";
        return new PrepareResponse(d.getOrderId(), d.getAmount(), orderName, gateway.clientKey(),
                d.getCustomerKey(), donor.getNickname());
    }

    /** 실패 상태 기록은 예외를 던져도 커밋되도록 롤백하지 않는다. */
    @Transactional(noRollbackFor = ApiException.class)
    public DonationResponse confirm(AuthUser user, ConfirmRequest req) {
        requireGateway();
        Donation d = donationRepository.findByOrderIdForUpdate(req.orderId())
                .orElseThrow(() -> new ApiException(ErrorCode.DONATION_NOT_FOUND));
        if (!d.isDonatedBy(user.id())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        if (d.isDone()) {
            // 새로고침 등으로 같은 승인 요청이 다시 와도 결과만 돌려준다.
            if (req.paymentKey().equals(d.getPaymentKey())) return DonationResponse.from(d);
            throw new ApiException(ErrorCode.DONATION_ALREADY_PROCESSED);
        }
        if (d.getStatus() == DonationStatus.FAILED) {
            throw new ApiException(ErrorCode.DONATION_ALREADY_PROCESSED);
        }
        // 브라우저가 보낸 금액이 주문 금액과 다르면 결제사에 승인 요청조차 하지 않는다.
        if (req.amount() != d.getAmount()) {
            throw new ApiException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        PaymentGateway.Payment payment;
        try {
            payment = gateway.confirm(req.paymentKey(), d.getOrderId(), d.getAmount());
        } catch (PaymentGateway.PaymentGatewayException e) {
            if ("ALREADY_PROCESSED_PAYMENT".equals(e.code())) {
                // 이미 승인된 결제(웹훅이 먼저 처리했거나 재시도) → 결제사에 조회해서 확정
                payment = gateway.find(req.paymentKey());
            } else if (e.clientError()) {
                d.fail(e.code() + ": " + e.getMessage());
                log.info("Payment rejected for order {}: {} {}", d.getOrderId(), e.code(), e.getMessage());
                throw new ApiException(ErrorCode.PAYMENT_FAILED);
            } else {
                throw new ApiException(ErrorCode.PAYMENT_GATEWAY_ERROR, e);
            }
        }
        verifyAndComplete(d, payment);
        return DonationResponse.from(d);
    }

    /** 결제사 웹훅. 본문은 단서로만 쓰고, 결제사 API 로 다시 조회한 결과로 판단한다. */
    @Transactional(noRollbackFor = ApiException.class)
    public void onPaymentNotification(String paymentKey, String orderId) {
        if (!gateway.isConfigured() || paymentKey == null || orderId == null) return;
        Donation d = donationRepository.findByOrderIdForUpdate(orderId).orElse(null);
        if (d == null || d.isDone()) return;
        PaymentGateway.Payment payment = gateway.find(paymentKey);
        if (!payment.isDone()) return;
        try {
            verifyAndComplete(d, payment);
        } catch (ApiException e) {
            log.warn("Webhook payment verification failed for order {}: {}", orderId, e.errorCode());
        }
    }

    @Transactional(readOnly = true)
    public DonationSummary summary(Long broadcastId) {
        broadcastRepository.findActiveById(broadcastId)
                .orElseThrow(() -> new ApiException(ErrorCode.BROADCAST_NOT_FOUND));
        var recent = donationRepository.findDone(broadcastId, PageRequest.of(0, 20)).stream()
                .map(DonationResponse::from).toList();
        return new DonationSummary(donationRepository.sumDone(broadcastId), donationRepository.countDone(broadcastId), recent);
    }

    private void verifyAndComplete(Donation d, PaymentGateway.Payment p) {
        boolean valid = p.isDone()
                && d.getOrderId().equals(p.orderId())
                && d.getAmount() == p.totalAmount();
        if (!valid) {
            log.warn("Payment mismatch for order {}: status={} orderId={} amount={}", d.getOrderId(), p.status(), p.orderId(), p.totalAmount());
            d.fail("verification failed");
            throw new ApiException(ErrorCode.PAYMENT_FAILED);
        }
        boolean newlyCredited = d.complete(p.paymentKey(), p.approvedAt() != null ? p.approvedAt() : clock.instant());
        if (newlyCredited && d.getBroadcast().isLive()) {
            events.publishEvent(new DonationRealtimeEvent(d.getBroadcast().getRoomName(), DonationResponse.from(d)));
        }
    }

    private void requireGateway() {
        if (!gateway.isConfigured()) throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED);
    }

    public record DonationRealtimeEvent(String roomName, DonationResponse donation) {}
}
