package com.threatdetection.gateway.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void uncommittedResponseReceivesJson500() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());

        StepVerifier.create(handler.handle(exchange,
                new IllegalStateException("password=secret token=secret-token")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("password=***")
                .contains("token=***")
                .doesNotContain("secret-token");
    }

    @Test
    void committedResponsePreservesOriginalFailureWithoutSecondWrite() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        DataBuffer body = exchange.getResponse().bufferFactory()
                .wrap("ok".getBytes(StandardCharsets.UTF_8));
        exchange.getResponse().writeWith(Mono.just(body)).block();
        RuntimeException original = new RuntimeException("late failure");

        assertThat(exchange.getResponse().isCommitted()).isTrue();
        StepVerifier.create(handler.handle(exchange, original))
                .expectErrorMatches(error -> error == original)
                .verify();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo("ok");
    }
}
