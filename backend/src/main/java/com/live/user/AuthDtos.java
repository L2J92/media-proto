package com.live.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record SignupRequest(
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(min = 8, max = 64, message = "비밀번호는 8~64자입니다.") String password,
            @NotBlank @Size(min = 2, max = 30, message = "닉네임은 2~30자입니다.") String nickname) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record MeResponse(Long id, String email, String nickname) {
        public static MeResponse from(AuthUser user) {
            return new MeResponse(user.id(), user.email(), user.nickname());
        }
    }
}
