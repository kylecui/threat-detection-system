package com.threatdetection.gateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * 全局异常处理器
 * 
 * <p>捕获并处理网关层面的所有异常
 * 
 * @author Threat Detection Team
 * @version 1.0
 */
@Slf4j
@Component
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        log.error("Global exception caught: {}", ex.getMessage(), ex);
        
        // 设置响应状态和类型
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        // 构建错误响应
        String errorMessage = String.format(
            "{\"error\":\"Internal Server Error\",\"message\":\"%s\",\"timestamp\":\"%s\",\"status\":%d}",
            sanitizeMessage(ex.getMessage()),
            Instant.now().toString(),
            HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        
        DataBuffer buffer = exchange.getResponse()
            .bufferFactory()
            .wrap(errorMessage.getBytes(StandardCharsets.UTF_8));
        
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * 清理错误消息，避免暴露敏感信息
     */
    private String sanitizeMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "An unexpected error occurred";
        }
        
        // 移除可能的敏感信息
        return message
            .replaceAll("(?i)password[\\s=:]+\\S+", "password=***")
            .replaceAll("(?i)token[\\s=:]+\\S+", "token=***")
            .substring(0, Math.min(message.length(), 200)); // 限制长度
    }
}
