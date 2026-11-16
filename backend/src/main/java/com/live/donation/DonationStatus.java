package com.live.donation;

public enum DonationStatus {
    /** 주문만 만들어진 상태. 결제창에서 결제 중 */
    PENDING,
    /** 결제 승인까지 확인되어 적립된 상태 */
    DONE,
    /** 승인 실패 */
    FAILED
}
