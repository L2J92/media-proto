package com.live.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.live.common.ErrorCode;
import com.live.common.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.io.IOException;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
                                                   SecurityContextRepository securityContextRepository) throws Exception {
        // SPA가 XSRF-TOKEN 쿠키를 읽어 X-XSRF-TOKEN 헤더로 보낸다. 토큰을 매 요청 로드해 쿠키가 항상 발급되게 한다.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler)
                        // LiveKit 웹훅은 서명(JWT)으로 따로 검증한다.
                        .ignoringRequestMatchers("/api/webhooks/**"))
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/webhooks/**").permitAll()
                        // 방송 목록·상세·시청 토큰, 채팅/녹화 조회는 비로그인 허용
                        // 시청 품질 샘플은 비로그인 시청자도 보낸다 (CSRF 토큰은 필요)
                        .requestMatchers(HttpMethod.POST, "/api/broadcasts/*/qoe").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/broadcasts/*/qoe").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/broadcasts/**", "/api/recordings/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, objectMapper, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, objectMapper, ErrorCode.FORBIDDEN)));
        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private static void writeError(HttpServletResponse res, ObjectMapper om, ErrorCode code) throws IOException {
        res.setStatus(code.status().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        om.writeValue(res.getOutputStream(), ErrorResponse.of(code));
    }
}
