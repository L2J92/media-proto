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
    INVALID_BROADCAST_STATE(HttpStatus.CONFLICT, "현재 방송 상태에서는 할 수 없는 작업입니다."),
    BROADCAST_NOT_LIVE(HttpStatus.CONFLICT, "방송 중이 아닙니다."),
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
