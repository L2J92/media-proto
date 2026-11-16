package com.live.user;

import java.io.Serial;
import java.io.Serializable;

/** 세션에 저장되는 로그인 사용자 정보. 비밀번호 등 민감 정보는 담지 않는다. */
public record AuthUser(Long id, String email, String nickname) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static AuthUser from(User user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getNickname());
    }
}
