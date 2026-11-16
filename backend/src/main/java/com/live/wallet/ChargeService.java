package com.live.wallet;

import com.live.broadcast.Broadcast;
import com.live.broadcast.BroadcastRepository;
import com.live.common.ApiException;
import com.live.common.ErrorCode;
import com.live.donation.DonationDtos.DonationResponse;
import com.live.donation.DonationService;
import com.live.payment.PaymentGateway;
import com.live.user.AuthUser;
import com.live.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

import static com.live.wallet.WalletDtos.*;

/**
 * 포인트 충전 흐름
 * 1) prepare: 서버가 주문(금액, 바로 후원이면 후원 대상)을 먼저 저장한다.
 * 2) 브라우저가 토스 결제창에서 결제한다.
 * 3) confirm: 주문을 잠그고 금액을 대조 → 결제사 승인 → 응답의 상태·금액·주문번호 재검증 → 충전(→ 바로 후원)
 * 4) webhook: 통지 내용은 믿지 않고 결제사에 재조회해 같은 방식으로 확정한다.
 * 주문은 PENDING → DONE 으로 한 번만 바뀌므로 같은 승인 통지가 반복돼도 포인트는 한 번만 충전된다.
 */
@Service
public class ChargeService {
    private static final Logger log = LoggerFactory.getLogger(ChargeService.class);

    private final ChargeOrderRepository orderRepository;
    private final BroadcastRepository broadcastRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final DonationService donationService;
    private final PaymentGateway gateway;
    private final Clock clock;

    public ChargeService(ChargeOrderRepository orderRepository, BroadcastRepository broadcastRepository,
                         UserRepository userRepository, WalletService walletService, DonationService donationService,
                         PaymentGateway gateway, Clock clock) {
        this.orderRepository = orderRepository;
        this.broadcastRepository = broadcastRepository;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.donationService = donationService;
        this.gateway = gateway;
        this.clock = clock;
    }

    @Transactional
    public ChargePrepareResponse prepare(AuthUser user, ChargeRequest req) {
        if (!gateway.isConfigured()) throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED);
        var donor = userRepository.findById(user.id()).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        String orderName = "포인트 " + String.format("%,d", req.amount()) + "P 충전";
        Long broadcastId = null;
        String message = null;
        if (req.donation() != null) {
            Broadcast b = broadcastRepository.findActiveById(req.donation().broadcastId())
                    .orElseThrow(() -> new ApiException(ErrorCode.BROADCAST_NOT_FOUND));
            if (b.isOwnedBy(user.id())) throw new ApiException(ErrorCode.CANNOT_DONATE_TO_SELF);
            broadcastId = b.getId();
            message = blankToNull(req.donation().message());
            orderName = b.getOwner().getNickname() + "님 후원 (" + String.format("%,d", req.amount()) + "P)";
        }
        ChargeOrder order = orderRepository.save(new ChargeOrder(user.id(), req.amount(),
                "chg_" + UUID.randomUUID().toString().replace("-", ""), "cust_" + UUID.randomUUID(),
                broadcastId, message));
        return new ChargePrepareResponse(order.getOrderId(), order.getAmount(), orderName, gateway.clientKey(),
                order.getCustomerKey(), donor.getNickname());
    }

    /** 실패 상태 기록은 예외를 던져도 커밋되도록 롤백하지 않는다. */
    @Transactional(noRollbackFor = ApiException.class)
    public ChargeResult confirm(AuthUser user, ConfirmRequest req) {
        if (!gateway.isConfigured()) throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED);
        ChargeOrder order = orderRepository.findByOrderIdForUpdate(req.orderId())
                .orElseThrow(() -> new ApiException(ErrorCode.CHARGE_ORDER_NOT_FOUND));
        if (!order.isOwnedBy(user.id())) throw new ApiException(ErrorCode.FORBIDDEN);
        if (order.isDone()) {
            // 새로고침 등으로 같은 승인 요청이 다시 와도 결과만 돌려준다.
            if (req.paymentKey().equals(order.getPaymentKey())) return result(order, false);
            throw new ApiException(ErrorCode.ORDER_ALREADY_PROCESSED);
        }
        if (order.getStatus() == ChargeStatus.FAILED) throw new ApiException(ErrorCode.ORDER_ALREADY_PROCESSED);
        // 브라우저가 보낸 금액이 주문 금액과 다르면 결제사에 승인 요청조차 하지 않는다.
        if (req.amount() != order.getAmount()) throw new ApiException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        PaymentGateway.Payment payment;
        try {
            payment = gateway.confirm(req.paymentKey(), order.getOrderId(), order.getAmount());
        } catch (PaymentGateway.PaymentGatewayException e) {
            if ("ALREADY_PROCESSED_PAYMENT".equals(e.code())) {
                payment = gateway.find(req.paymentKey());
            } else if (e.clientError()) {
                order.fail(e.code() + ": " + e.getMessage());
                log.info("Payment rejected for order {}: {} {}", order.getOrderId(), e.code(), e.getMessage());
                throw new ApiException(ErrorCode.PAYMENT_FAILED);
            } else {
                throw new ApiException(ErrorCode.PAYMENT_GATEWAY_ERROR, e);
            }
        }
        return result(order, verifyAndComplete(order, payment));
    }

    /** 결제사 웹훅. 본문은 단서로만 쓰고, 결제사 API 로 다시 조회한 결과로 판단한다. */
    @Transactional(noRollbackFor = ApiException.class)
    public void onPaymentNotification(String paymentKey, String orderId) {
        if (!gateway.isConfigured() || paymentKey == null || orderId == null) return;
        ChargeOrder order = orderRepository.findByOrderIdForUpdate(orderId).orElse(null);
        if (order == null || order.isDone()) return;
        PaymentGateway.Payment payment = gateway.find(paymentKey);
        if (!payment.isDone()) return;
        try {
            verifyAndComplete(order, payment);
        } catch (ApiException e) {
            log.warn("Webhook payment verification failed for order {}: {}", orderId, e.errorCode());
        }
    }

    /** @return 이번 호출로 새로 확정됐는지 */
    private boolean verifyAndComplete(ChargeOrder order, PaymentGateway.Payment p) {
        boolean valid = p.isDone() && order.getOrderId().equals(p.orderId()) && order.getAmount() == p.totalAmount();
        if (!valid) {
            log.warn("Payment mismatch for order {}: status={} orderId={} amount={}", order.getOrderId(), p.status(), p.orderId(), p.totalAmount());
            order.fail("verification failed");
            throw new ApiException(ErrorCode.PAYMENT_FAILED);
        }
        if (!order.complete(p.paymentKey(), p.approvedAt() != null ? p.approvedAt() : clock.instant())) return false;
        walletService.charge(order.getUserId(), order.getAmount(), order.getId());
        if (order.hasDonation()) {
            // 결제가 끝난 뒤 방송이 삭제됐으면 후원은 건너뛰고 포인트만 충전해 둔다(돈은 지갑에 남음).
            donationService.donateFromCharge(order);
        }
        return true;
    }

    private ChargeResult result(ChargeOrder order, boolean fresh) {
        DonationResponse donation = order.hasDonation() ? donationService.findByChargeOrder(order.getId()) : null;
        long balance = walletService.get(order.getUserId()).balance();
        return new ChargeResult(order.getOrderId(), order.getAmount(), balance, donation);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
