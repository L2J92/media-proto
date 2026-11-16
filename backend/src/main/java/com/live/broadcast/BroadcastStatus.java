package com.live.broadcast;

public enum BroadcastStatus {
    /** 등록만 된 상태. 소유자가 시작할 수 있다. */
    READY,
    /** 송출 중. 시청 토큰을 발급한다. */
    LIVE,
    /** 종료됨. 다시 시작할 수 없다(새 방송을 등록한다). */
    ENDED
}
