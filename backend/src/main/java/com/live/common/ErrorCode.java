package com.live.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_BROADCAST_OWNER(HttpStatus.FORBIDDEN, "방송 소유자만 할 수 있습니다."),
    NOT_CHAT_AUTHOR(HttpStatus.FORBIDDEN, "작성자만 할 수 있습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    BROADCAST_NOT_FOUND(HttpStatus.NOT_FOUND, "방송을 찾을 수 없습니다."),
    CHAT_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅을 찾을 수 없습니다."),
    RECORDING_NOT_FOUND(HttpStatus.NOT_FOUND, "녹화 영상을 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    CHANNEL_NOT_FOUND(HttpStatus.NOT_FOUND, "채널을 찾을 수 없습니다."),
    DUPLICATE_HANDLE(HttpStatus.CONFLICT, "이미 사용 중인 채널 주소입니다."),
    CANNOT_FOLLOW_SELF(HttpStatus.BAD_REQUEST, "자신의 채널은 팔로우할 수 없습니다."),
    INVALID_BROADCAST_STATE(HttpStatus.CONFLICT, "현재 방송 상태에서는 할 수 없는 작업입니다."),
    BROADCAST_NOT_LIVE(HttpStatus.CONFLICT, "방송 중이 아닙니다."),
    TOO_MANY_CHATS(HttpStatus.TOO_MANY_REQUESTS, "채팅을 너무 빠르게 보내고 있습니다. 잠시 후 다시 시도하세요."),
    CHARGE_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "충전 주문을 찾을 수 없습니다."),
    INSUFFICIENT_POINTS(HttpStatus.CONFLICT, "포인트가 부족합니다."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "이미 처리 중인 요청입니다."),
    CANNOT_DONATE_TO_SELF(HttpStatus.BAD_REQUEST, "자신의 방송에는 후원할 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 주문 금액과 다릅니다."),
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "결제 승인에 실패했습니다."),
    ORDER_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 결제 주문입니다."),
    PAYMENT_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "결제 설정이 없습니다."),
    PAYMENT_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "결제 서버와 통신하지 못했습니다."),
    MEDIA_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "미디어 서버와 통신하지 못했습니다."),
    MEDIA_SERVER_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "미디어 서버 설정이 없습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String message() { return message; }
}
