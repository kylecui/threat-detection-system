package com.threatdetection.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * 请求日志过滤器
 * 
 * <p>记录所有经过网关的请求，包括：
 * <ul>
 *   <li>请求路径</li>
 *   <li>HTTP方法</li>
 *   <li>客户端IP</li>
 *   <li>请求耗时</li>
 *   <li>响应状态码</li>
 * </ul>
 * 
 * @author Threat Detection Team
 * @version 1.0
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long startTime = System.currentTimeMillis();
        
        // 提取请求信息
        String path = request.getPath().value();
        String method = request.getMethod().name();
        String clientIp = getClientIp(request);
        String requestId = request.getId();
        
        // 记录请求开始
        log.info("Request started - ID: {}, Method: {}, Path: {}, ClientIP: {}", 
                 requestId, method, path, clientIp);
        
        // 继续处理请求，并在完成后记录响应
        return chain.filter(exchange).then(
            Mono.fromRunnable(() -> {
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                int statusCode = exchange.getResponse().getStatusCode() != null ? 
                                 exchange.getResponse().getStatusCode().value() : 0;
                
                log.info("Request completed - ID: {}, Method: {}, Path: {}, Status: {}, Duration: {}ms",
                         requestId, method, path, statusCode, duration);
            })
        );
    }

    /**
     * 获取客户端真实IP
     * 
     * <p>优先从X-Forwarded-For获取，然后从X-Real-IP，最后从RemoteAddress
     * 
     * @param request ServerHttpRequest
     * @return 客户端IP
     */
    private String getClientIp(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        
        // 1. X-Forwarded-For (代理/负载均衡器添加)
        String xForwardedFor = headers.getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        // 2. X-Real-IP (Nginx添加)
        String xRealIp = headers.getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // 3. RemoteAddress (直连)
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }

    @Override
    public int getOrder() {
        // 最高优先级，第一个执行
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
