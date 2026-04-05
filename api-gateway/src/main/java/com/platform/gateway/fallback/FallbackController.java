package com.platform.gateway.fallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/orders")
    public Mono<ResponseEntity<Map<String, Object>>> ordersFallback() {
        log.warn("Circuit breaker triggered for order-service");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildFallback("order-service", "Order service is temporarily unavailable. Please try again later.")));
    }

    @GetMapping("/payments")
    public Mono<ResponseEntity<Map<String, Object>>> paymentsFallback() {
        log.warn("Circuit breaker triggered for payment-service");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildFallback("payment-service", "Payment service is temporarily unavailable. Please try again later.")));
    }

    @GetMapping("/inventory")
    public Mono<ResponseEntity<Map<String, Object>>> inventoryFallback() {
        log.warn("Circuit breaker triggered for inventory-service");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildFallback("inventory-service", "Inventory service is temporarily unavailable.")));
    }

    @GetMapping("/notifications")
    public Mono<ResponseEntity<Map<String, Object>>> notificationsFallback() {
        log.warn("Circuit breaker triggered for notification-service");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildFallback("notification-service", "Notification service is temporarily unavailable.")));
    }

    private Map<String, Object> buildFallback(String service, String message) {
        return Map.of(
            "success",   false,
            "service",   service,
            "message",   message,
            "timestamp", LocalDateTime.now().toString(),
            "statusCode", 503
        );
    }
}
