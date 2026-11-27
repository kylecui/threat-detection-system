package com.threatdetection.gateway.auth.service;

import com.threatdetection.gateway.auth.dto.LoginRequest;
import com.threatdetection.gateway.auth.dto.LoginResponse;
import com.threatdetection.gateway.auth.dto.BootstrapRequest;
import com.threatdetection.gateway.auth.entity.AuthRole;
import com.threatdetection.gateway.auth.entity.AuthUser;
import com.threatdetection.gateway.auth.repository.AuthRoleRepository;
import com.threatdetection.gateway.auth.repository.AuthUserRepository;
import com.threatdetection.gateway.auth.validation.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.r2dbc.core.DatabaseClient;
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
    private final DatabaseClient databaseClient;
    private final AccountLifecycleService accountLifecycleService;

    public Mono<Boolean> needsBootstrap() {
        return userRepository.count().map(count -> count == 0L);
    }

    /**
     * Atomically creates the first SUPER_ADMIN. A PostgreSQL advisory lock and
     * the empty-table predicate make concurrent bootstrap attempts safe.
     */
    public Mono<AuthUser> bootstrap(BootstrapRequest request) {
        validateBootstrapRequest(request);
        String username = request.getUsername().trim();
        String passwordHash = passwordEncoder.encode(request.getPassword());
        String displayName = request.getDisplayName() == null ? "" : request.getDisplayName().trim();
        String email = request.getEmail() == null ? "" : request.getEmail().trim();

        String sql = "WITH bootstrap_lock AS MATERIALIZED (" +
                "SELECT pg_advisory_xact_lock(7301162026)), " +
                "new_user AS (" +
                "INSERT INTO auth_users (username, password_hash, display_name, email, customer_id, enabled) " +
                "SELECT :username, :passwordHash, :displayName, :email, NULL, TRUE FROM bootstrap_lock " +
                "WHERE NOT EXISTS (SELECT 1 FROM auth_users) RETURNING id), " +
                "role_link AS (" +
                "INSERT INTO auth_user_roles (user_id, role_id) " +
                "SELECT new_user.id, auth_roles.id FROM new_user JOIN auth_roles ON auth_roles.name = 'SUPER_ADMIN' " +
                "RETURNING user_id) SELECT user_id FROM role_link";

        return databaseClient.sql(sql)
                .bind("username", username)
                .bind("passwordHash", passwordHash)
                .bind("displayName", displayName)
                .bind("email", email)
                .map((row, metadata) -> row.get("user_id", Long.class))
                .one()
                .switchIfEmpty(Mono.error(new BootstrapAlreadyCompletedException()))
                .flatMap(userRepository::findById)
                .doOnSuccess(user -> log.info("Initial SUPER_ADMIN created: user={}", user.getUsername()));
    }

    private void validateBootstrapRequest(BootstrapRequest request) {
        if (request == null || request.getUsername() == null ||
                !request.getUsername().trim().matches("[A-Za-z0-9_.-]{3,64}")) {
            throw new IllegalArgumentException("Username must be 3-64 characters using letters, digits, dot, underscore or hyphen");
        }
        String password = request.getPassword();
        if (!PasswordPolicy.isValid(password)) {
            throw new IllegalArgumentException("Password must be " + PasswordPolicy.DESCRIPTION);
        }
    }

    public static final class BootstrapAlreadyCompletedException extends RuntimeException {
        public BootstrapAlreadyCompletedException() {
            super("Platform bootstrap has already been completed");
        }
    }

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
                    return requireOperationalAccount(user).flatMap(this::buildLoginResponse);
                });
    }

    public Mono<LoginResponse> refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            return Mono.error(new RuntimeException("Invalid refresh token"));
        }
        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(this::requireOperationalAccount)
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
                                .tenantId(user.getTenantId())
                                .roles(roles)
                                .build()));
    }

    private Mono<LoginResponse> buildLoginResponse(AuthUser user) {
        return roleRepository.findRolesByUserId(user.getId())
                .map(AuthRole::getName)
                .collectList()
                .map(roles -> {
                    String token = jwtTokenProvider.generateToken(
                            user.getId(), user.getUsername(), user.getCustomerId(),
                            user.getTenantId(), roles);
                    String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

                    log.info("Login successful: user={}, roles={}, customerId={}, tenantId={}",
                            user.getUsername(), roles, user.getCustomerId(), user.getTenantId());

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
                                    .tenantId(user.getTenantId())
                                    .roles(roles)
                                    .build())
                            .build();
                });
    }

    private Mono<AuthUser> requireOperationalAccount(AuthUser user) {
        return accountLifecycleService.isOperational(user)
                .flatMap(active -> active
                        ? Mono.just(user)
                        : Mono.error(new RuntimeException(
                                "Account, tenant or customer is inactive or suspended")));
    }
}
