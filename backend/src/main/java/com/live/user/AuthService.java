package com.live.user;

import com.live.common.ApiException;
import com.live.common.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthUser signup(AuthDtos.SignupRequest req) {
        String email = normalize(req.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
        }
        try {
            User user = userRepository.saveAndFlush(
                    new User(email, passwordEncoder.encode(req.password()), req.nickname().strip()));
            return AuthUser.from(user);
        } catch (DataIntegrityViolationException e) {
            // 동시 가입 요청으로 unique 제약에 걸린 경우
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    @Transactional(readOnly = true)
    public AuthUser authenticate(String rawEmail, String password) {
        User user = userRepository.findByEmail(normalize(rawEmail))
                .orElseThrow(() -> new ApiException(ErrorCode.LOGIN_FAILED));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.LOGIN_FAILED);
        }
        return AuthUser.from(user);
    }

    /** 평가용 데모 계정. 없으면 만들고 로그인 정보로 돌려준다. */
    @Transactional
    public AuthUser demoUser(DemoAccount account) {
        User user = userRepository.findByEmail(account.email()).orElseGet(() ->
                userRepository.save(new User(account.email(),
                        passwordEncoder.encode(java.util.UUID.randomUUID().toString()), account.nickname())));
        return AuthUser.from(user);
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase();
    }

    public enum DemoAccount {
        STREAMER("streamer@demo.live", "데모방송자"),
        STREAMER2("streamer2@demo.live", "데모방송자2"),
        VIEWER("viewer@demo.live", "데모시청자");

        private final String email;
        private final String nickname;

        DemoAccount(String email, String nickname) {
            this.email = email;
            this.nickname = nickname;
        }

        public String email() { return email; }
        public String nickname() { return nickname; }
    }
}
