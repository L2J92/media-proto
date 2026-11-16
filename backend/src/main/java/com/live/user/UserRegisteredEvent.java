package com.live.user;

/** 회원이 새로 만들어졌을 때(같은 트랜잭션 안에서) 발행. 다른 모듈은 user 패키지를 몰라도 된다. */
public record UserRegisteredEvent(Long userId) {}
