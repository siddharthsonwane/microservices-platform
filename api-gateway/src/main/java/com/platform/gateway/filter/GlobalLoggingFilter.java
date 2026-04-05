package com.platform.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
public class GlobalLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String traceId   = request.getHeaders().getFirst("X-Trace-Id");
        String requestId = request.getHeaders().getFirst("X-Request-Id");
        if (traceId == null)   traceId   = UUID.randomUUID().toString();
        if (requestId == null) requestId = UUID.randomUUID().toString();

        final String finalTraceId   = traceId;
        final String finalRequestId = requestId;
        final long   start          = System.currentTimeMillis();

        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-Trace-Id",   finalTraceId)
                .header("X-Request-Id", finalRequestId)
                .build();

        log.info("GATEWAY ▶ {} {} | traceId={} | headers={}",
                request.getMethod(),
                request.getURI(),
                finalTraceId,
                request.getHeaders().toSingleValueMap().keySet());

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .doFinally(signal -> {
                    long elapsed = System.currentTimeMillis() - start;
                    log.info("GATEWAY ◀ {} {} → {} | traceId={} | elapsed={}ms",
                            request.getMethod(),
                            request.getURI(),
                            exchange.getResponse().getStatusCode(),
                            finalTraceId,
                            elapsed);
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
