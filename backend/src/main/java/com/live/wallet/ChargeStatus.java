package com.live.wallet;

public enum ChargeStatus {
    /** 주문만 만들어진 상태. 결제창에서 결제 중 */
    PENDING,
    /** 결제 승인까지 확인되어 포인트가 충전된 상태 */
    DONE,
    /** 승인 실패 */
    FAILED
}
