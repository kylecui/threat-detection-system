package com.threatdetection.gateway.auth.service;

import com.threatdetection.gateway.auth.dto.LoginRequest;
import com.threatdetection.gateway.auth.dto.LoginResponse;
import com.threatdetection.gateway.auth.entity.AuthRole;
import com.threatdetection.gateway.auth.entity.AuthUser;
import com.threatdetection.gateway.auth.repository.AuthRoleRepository;
import com.threatdetection.gateway.auth.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Authentication service — login, refresh, user info
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthUserRepository userRepository;
    private final AuthRoleRepository roleRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public Mono<LoginResponse> login(LoginRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .switchIfEmpty(Mono.error(new RuntimeException("Invalid credentials")))
                .flatMap(user -> {
                    if (!user.getEnabled()) {
                        return Mono.error(new RuntimeException("Account disabled"));
                    }
                    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                        log.warn("Login failed for user: {}", request.getUsername());
                        return Mono.error(new RuntimeException("Invalid credentials"));
                    }
                    return buildLoginResponse(user);
                });
    }

    public Mono<LoginResponse> refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            return Mono.error(new RuntimeException("Invalid refresh token"));
        }
        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(this::buildLoginResponse);
    }

    public Mono<LoginResponse.UserInfo> getUserInfo(String username) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> roleRepository.findRolesByUserId(user.getId())
                        .map(AuthRole::getName)
                        .collectList()
                        .map(roles -> LoginResponse.UserInfo.builder()
                                .id(user.getId())
                                .username(user.getUsername())
                                .displayName(user.getDisplayName())
                                .email(user.getEmail())
                                .customerId(user.getCustomerId())
                                .roles(roles)
                                .build()));
    }

    private Mono<LoginResponse> buildLoginResponse(AuthUser user) {
        return roleRepository.findRolesByUserId(user.getId())
                .map(AuthRole::getName)
                .collectList()
                .map(roles -> {
                    String token = jwtTokenProvider.generateToken(
                            user.getId(), user.getUsername(), user.getCustomerId(), roles);
                    String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

                    log.info("Login successful: user={}, roles={}, customerId={}",
                            user.getUsername(), roles, user.getCustomerId());

                    return LoginResponse.builder()
                            .token(token)
                            .refreshToken(refreshToken)
                            .expiresIn(jwtTokenProvider.getExpirationMs() / 1000)
                            .user(LoginResponse.UserInfo.builder()
                                    .id(user.getId())
                                    .username(user.getUsername())
                                    .displayName(user.getDisplayName())
                                    .email(user.getEmail())
                                    .customerId(user.getCustomerId())
                                    .roles(roles)
                                    .build())
                            .build();
                });
    }
}
