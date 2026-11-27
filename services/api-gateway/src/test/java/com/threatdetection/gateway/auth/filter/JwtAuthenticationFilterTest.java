package com.threatdetection.gateway.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.threatdetection.gateway.auth.entity.AuthUser;
import com.threatdetection.gateway.auth.repository.AuthUserRepository;
import com.threatdetection.gateway.auth.service.AccountLifecycleService;
import com.threatdetection.gateway.auth.service.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class JwtAuthenticationFilterTest {

    private JwtTokenProvider jwtTokenProvider;
    private AuthUserRepository authUserRepository;
    private AccountLifecycleService accountLifecycleService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        authUserRepository = mock(AuthUserRepository.class);
        accountLifecycleService = mock(AccountLifecycleService.class);
        filter = new JwtAuthenticationFilter(
                jwtTokenProvider, authUserRepository, accountLifecycleService);
    }

    @Test
    void successfulAuthenticatedResponseMustNotBeRewrittenAsUnauthorized() {
        AuthUser user = activeUser();
        Claims claims = claimsFor(user);
        MockServerWebExchange exchange = authenticatedExchange("/api/v1/customers?page=0&size=20");
        AtomicReference<ServerWebExchange> downstreamExchange = new AtomicReference<>();

        when(jwtTokenProvider.parseToken("valid-token")).thenReturn(claims);
        when(authUserRepository.findById(user.getId())).thenReturn(Mono.just(user));
        when(accountLifecycleService.isOperational(user)).thenReturn(Mono.just(true));

        GatewayFilterChain chain = routedExchange -> {
            downstreamExchange.set(routedExchange);
            routedExchange.getResponse().setStatusCode(HttpStatus.OK);
            DataBuffer body = routedExchange.getResponse().bufferFactory()
                    .wrap("{\"content\":[]}".getBytes(StandardCharsets.UTF_8));
            return routedExchange.getResponse().writeWith(Mono.just(body));
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo("{\"content\":[]}");
        assertThat(downstreamExchange.get()).isNotNull();
        assertThat(downstreamExchange.get().getRequest().getHeaders().getFirst("X-Auth-Username"))
                .isEqualTo("admin");
        assertThat(downstreamExchange.get().getRequest().getHeaders().getFirst("X-Auth-Roles"))
                .isEqualTo("SUPER_ADMIN");
    }

    @Test
    void missingAccountReturns401BeforeCallingDownstream() {
        Claims claims = baseClaims(1L, "deleted-admin");
        MockServerWebExchange exchange = authenticatedExchange("/api/v1/users");
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        when(jwtTokenProvider.parseToken("valid-token")).thenReturn(claims);
        when(authUserRepository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"UNAUTHORIZED\"")
                .contains("Account no longer exists");
        verify(chain, never()).filter(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void inactiveAccountReturns401BeforeCallingDownstream() {
        AuthUser user = activeUser();
        Claims claims = claimsFor(user);
        MockServerWebExchange exchange = authenticatedExchange("/api/v1/alerts");
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        when(jwtTokenProvider.parseToken("valid-token")).thenReturn(claims);
        when(authUserRepository.findById(user.getId())).thenReturn(Mono.just(user));
        when(accountLifecycleService.isOperational(user)).thenReturn(Mono.just(false));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("Account or customer is no longer active");
        verify(chain, never()).filter(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingBearerTokenReturnsStableUnauthorizedResponse() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/alerts").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"UNAUTHORIZED\"");
        verify(chain, never()).filter(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void expiredBearerTokenReturnsStableTokenExpiredResponse() {
        MockServerWebExchange exchange = authenticatedExchange("/api/v1/alerts");
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(jwtTokenProvider.parseToken("valid-token"))
                .thenThrow(mock(ExpiredJwtException.class));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"TOKEN_EXPIRED\"");
        verify(chain, never()).filter(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void malformedBearerTokenReturnsStableTokenInvalidResponse() {
        MockServerWebExchange exchange = authenticatedExchange("/api/v1/alerts");
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(jwtTokenProvider.parseToken("valid-token"))
                .thenThrow(new MalformedJwtException("bad token"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"TOKEN_INVALID\"");
        verify(chain, never()).filter(org.mockito.ArgumentMatchers.any());
    }

    private MockServerWebExchange authenticatedExchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build());
    }

    private AuthUser activeUser() {
        return AuthUser.builder()
                .id(1L)
                .username("admin")
                .enabled(true)
                .build();
    }

    private Claims claimsFor(AuthUser user) {
        return baseClaims(user.getId(), user.getUsername());
    }

    private Claims baseClaims(Long userId, String username) {
        Claims claims = Jwts.claims();
        claims.setSubject(username);
        claims.put("userId", userId);
        claims.put("roles", List.of("SUPER_ADMIN"));
        return claims;
    }
}
