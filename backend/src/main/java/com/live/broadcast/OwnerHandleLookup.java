package com.live.broadcast;

import java.util.Collection;
import java.util.Map;

/** 방송 소유자의 채널 주소(handle)를 찾는다. 채널 모듈이 구현한다(broadcast → channel 의존 방지). */
public interface OwnerHandleLookup {
    Map<Long, String> handles(Collection<Long> ownerIds);
}
