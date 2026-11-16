package com.live.user;

import com.live.common.ApiException;
import com.live.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;
    private final boolean demoAccountsEnabled;

    public AuthController(AuthService authService,
                          SecurityContextRepository securityContextRepository,
                          @Value("${app.demo-accounts-enabled}") boolean demoAccountsEnabled) {
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
        this.demoAccountsEnabled = demoAccountsEnabled;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthDtos.MeResponse> signup(@Valid @RequestBody AuthDtos.SignupRequest req,
                                                      HttpServletRequest request, HttpServletResponse response) {
        AuthUser user = authService.signup(req);
        establishSession(user, request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthDtos.MeResponse.from(user));
    }

    @PostMapping("/login")
    public AuthDtos.MeResponse login(@Valid @RequestBody AuthDtos.LoginRequest req,
                                     HttpServletRequest request, HttpServletResponse response) {
        AuthUser user = authService.authenticate(req.email(), req.password());
        establishSession(user, request, response);
        return AuthDtos.MeResponse.from(user);
    }

    @PostMapping("/demo-login")
    public AuthDtos.MeResponse demoLogin(@RequestParam AuthService.DemoAccount account,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (!demoAccountsEnabled) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        AuthUser user = authService.demoUser(account);
        establishSession(user, request, response);
        return AuthDtos.MeResponse.from(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthDtos.MeResponse me(@AuthenticationPrincipal AuthUser user) {
        if (user == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return AuthDtos.MeResponse.from(user);
    }

    private void establishSession(AuthUser user, HttpServletRequest request, HttpServletResponse response) {
        // 세션 고정 공격 방지: 기존 세션이 있으면 ID를 바꾼다.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        var auth = UsernamePasswordAuthenticationToken.authenticated(user, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
