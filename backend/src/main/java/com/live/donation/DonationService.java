package com.live.donation;

import com.live.broadcast.Broadcast;
import com.live.broadcast.BroadcastRepository;
import com.live.common.ApiException;
import com.live.common.ErrorCode;
import com.live.user.AuthUser;
import com.live.user.User;
import com.live.user.UserRepository;
import com.live.wallet.ChargeOrder;
import com.live.wallet.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.live.donation.DonationDtos.*;

/**
 * 포인트 후원. 후원 대상(방송 소유자)과 금액을 서버가 확인하고,
 * 후원 기록 생성과 두 지갑의 포인트 이동을 한 트랜잭션에서 처리한다.
 */
@Service
public class DonationService {
    private static final Logger log = LoggerFactory.getLogger(DonationService.class);

    private final DonationRepository donationRepository;
    private final BroadcastRepository broadcastRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final ApplicationEventPublisher events;

    public DonationService(DonationRepository donationRepository, BroadcastRepository broadcastRepository,
                           UserRepository userRepository, WalletService walletService,
                           ApplicationEventPublisher events) {
        this.donationRepository = donationRepository;
        this.broadcastRepository = broadcastRepository;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.events = events;
    }

    @Transactional
    public DonateResult donate(AuthUser user, Long broadcastId, DonateRequest req) {
        // 같은 요청(더블 클릭, 재전송)은 처음 결과를 그대로 돌려준다.
        var existing = donationRepository.findByRequestId(req.requestId());
        if (existing.isPresent()) {
            Donation d = existing.get();
            if (!d.isDonatedBy(user.id())) throw new ApiException(ErrorCode.DUPLICATE_REQUEST);
            return new DonateResult(DonationResponse.from(d), walletService.get(user.id()).balance());
        }
        Broadcast b = broadcastRepository.findActiveById(broadcastId)
                .orElseThrow(() -> new ApiException(ErrorCode.BROADCAST_NOT_FOUND));
        if (b.isOwnedBy(user.id())) throw new ApiException(ErrorCode.CANNOT_DONATE_TO_SELF);
        User donor = userRepository.findById(user.id()).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        Donation d;
        try {
            d = donationRepository.saveAndFlush(new Donation(b, donor, req.amount(), blankToNull(req.message()), req.requestId(), null));
        } catch (DataIntegrityViolationException e) {
            // 같은 requestId 가 동시에 들어온 경우
            throw new ApiException(ErrorCode.DUPLICATE_REQUEST);
        }
        // 잔액이 부족하면 예외 → 후원 기록까지 롤백
        long balance = walletService.transferForDonation(donor.getId(), b.getOwner().getId(), d.getAmount(), d.getId(),
                b.getOwner().getNickname(), donor.getNickname());
        announce(d);
        return new DonateResult(DonationResponse.from(d), balance);
    }

    /**
     * "충전하고 바로 후원": 충전 확정과 같은 트랜잭션에서 호출된다.
     * 결제 사이에 방송이 삭제됐으면 후원은 하지 않고 충전된 포인트만 남긴다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void donateFromCharge(ChargeOrder order) {
        if (donationRepository.findByChargeOrderId(order.getId()).isPresent()) return;
        Broadcast b = broadcastRepository.findActiveById(order.getDonationBroadcastId()).orElse(null);
        if (b == null || b.isOwnedBy(order.getUserId())) {
            log.info("Skip donation for charge {}: broadcast unavailable", order.getOrderId());
            return;
        }
        User donor = userRepository.findById(order.getUserId()).orElseThrow();
        Donation d = donationRepository.save(new Donation(b, donor, order.getAmount(), order.getDonationMessage(),
                "charge-" + order.getOrderId(), order.getId()));
        donationRepository.flush();
        walletService.transferForDonation(donor.getId(), b.getOwner().getId(), d.getAmount(), d.getId(),
                b.getOwner().getNickname(), donor.getNickname());
        announce(d);
    }

    @Transactional(readOnly = true)
    public DonationResponse findByChargeOrder(Long chargeOrderId) {
        return donationRepository.findByChargeOrderId(chargeOrderId).map(DonationResponse::from).orElse(null);
    }

    @Transactional(readOnly = true)
    public DonationSummary summary(Long broadcastId) {
        broadcastRepository.findActiveById(broadcastId)
                .orElseThrow(() -> new ApiException(ErrorCode.BROADCAST_NOT_FOUND));
        var recent = donationRepository.findRecent(broadcastId, PageRequest.of(0, 20)).stream()
                .map(DonationResponse::from).toList();
        return new DonationSummary(donationRepository.sumByBroadcast(broadcastId),
                donationRepository.countByBroadcastId(broadcastId), recent);
    }

    private void announce(Donation d) {
        events.publishEvent(new DonationRealtimeEvent(d.getBroadcast().getId(), DonationResponse.from(d)));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    public record DonationRealtimeEvent(Long broadcastId, DonationResponse donation) {}
}
