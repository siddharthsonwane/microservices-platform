package com.platform.inventory.application.service;

import com.platform.common.event.*;
import com.platform.inventory.domain.entity.*;
import com.platform.inventory.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository        itemRepository;
    private final InventoryReservationRepository reservationRepository;
    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;
    private final MeterRegistry                  meterRegistry;

    // ─── Reserve Inventory (Saga Step 1) ─────────────────────────────────────
    @Transactional
    public void reserveInventory(OrderCreatedEvent event) {
        // Idempotency: skip if already processed
        if (reservationRepository.existsByOrderId(event.getOrderId())) {
            log.warn("Inventory already reserved for orderId: {} — skipping", event.getOrderId());
            return;
        }

        log.info("Reserving inventory | orderId={} sagaId={} items={}",
                event.getOrderId(), event.getSagaId(), event.getItems().size());

        List<String> failedProducts = new ArrayList<>();

        for (OrderCreatedEvent.OrderItem orderItem : event.getItems()) {
            try {
                // Pessimistic lock to prevent race conditions / overselling
                InventoryItem item = itemRepository
                        .findByProductIdForUpdate(orderItem.getProductId())
                        .orElseThrow(() -> new RuntimeException(
                                "Product not found: " + orderItem.getProductId()));

                if (!item.canReserve(orderItem.getQuantity())) {
                    failedProducts.add(orderItem.getProductId() +
                            " (available=" + item.getAvailableQuantity() +
                            ", requested=" + orderItem.getQuantity() + ")");
                    continue;
                }

                item.reserve(orderItem.getQuantity());
                itemRepository.save(item);

                InventoryReservation reservation = InventoryReservation.builder()
                        .orderId(event.getOrderId())
                        .sagaId(event.getSagaId())
                        .productId(orderItem.getProductId())
                        .quantity(orderItem.getQuantity())
                        .build();
                reservationRepository.save(reservation);

            } catch (Exception ex) {
                log.error("Failed to reserve product {}: {}", orderItem.getProductId(), ex.getMessage());
                failedProducts.add(orderItem.getProductId());
            }
        }

        if (failedProducts.isEmpty()) {
            // All items reserved successfully
            String reservationId = UUID.randomUUID().toString();
            InventoryReservedEvent successEvent = new InventoryReservedEvent();
            successEvent.setOrderId(event.getOrderId());
            successEvent.setSagaId(event.getSagaId());
            successEvent.setReservationId(reservationId);

            kafkaTemplate.send(KafkaTopics.INVENTORY_RESERVED, event.getOrderId(), successEvent);
            meterRegistry.counter("inventory.reserved").increment();
            log.info("✅ Inventory reserved | orderId={}", event.getOrderId());
        } else {
            // Rollback any partial reservations
            rollbackReservations(event.getOrderId(), event.getSagaId());

            String reason = "Insufficient stock for: " + String.join(", ", failedProducts);
            InventoryFailedEvent failEvent = new InventoryFailedEvent();
            failEvent.setOrderId(event.getOrderId());
            failEvent.setSagaId(event.getSagaId());
            failEvent.setReason(reason);

            kafkaTemplate.send(KafkaTopics.INVENTORY_FAILED, event.getOrderId(), failEvent);
            meterRegistry.counter("inventory.reservation.failed").increment();
            log.warn("❌ Inventory reservation failed | orderId={} reason={}", event.getOrderId(), reason);
        }
    }

    // ─── Release Inventory (Compensating Transaction) ────────────────────────
    @Transactional
    public void releaseInventory(String orderId, String sagaId) {
        log.info("Releasing inventory for orderId={} (compensation)", orderId);

        List<InventoryReservation> reservations = reservationRepository.findByOrderId(orderId);
        for (InventoryReservation reservation : reservations) {
            if (reservation.getStatus() == ReservationStatus.RESERVED) {
                itemRepository.findByProductIdForUpdate(reservation.getProductId())
                        .ifPresent(item -> {
                            item.release(reservation.getQuantity());
                            itemRepository.save(item);
                        });
                reservation.release();
                reservationRepository.save(reservation);
            }
        }
        meterRegistry.counter("inventory.released").increment();
        log.info("Inventory released for orderId={}", orderId);
    }

    // ─── Confirm Sale (after order completion) ────────────────────────────────
    @Transactional
    public void confirmSale(String orderId) {
        List<InventoryReservation> reservations = reservationRepository.findByOrderId(orderId);
        for (InventoryReservation reservation : reservations) {
            if (reservation.getStatus() == ReservationStatus.RESERVED) {
                itemRepository.findByProductId(reservation.getProductId())
                        .ifPresent(item -> {
                            item.confirmSale(reservation.getQuantity());
                            itemRepository.save(item);
                        });
                reservation.confirm();
                reservationRepository.save(reservation);
            }
        }
        log.info("Sale confirmed for orderId={}", orderId);
    }

    // ─── Stock Management ─────────────────────────────────────────────────────
    @Transactional
    public InventoryItem addStock(String productId, String productName, int quantity) {
        InventoryItem item = itemRepository.findByProductId(productId)
                .orElse(InventoryItem.builder()
                        .productId(productId)
                        .productName(productName)
                        .availableQuantity(0)
                        .build());
        item.setAvailableQuantity(item.getAvailableQuantity() + quantity);
        return itemRepository.save(item);
    }

    // ─── Low stock check ─────────────────────────────────────────────────────
    public List<InventoryItem> getLowStockItems(int threshold) {
        return itemRepository.findByAvailableQuantityLessThan(threshold);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────
    private void rollbackReservations(String orderId, String sagaId) {
        List<InventoryReservation> partial = reservationRepository.findByOrderId(orderId);
        for (InventoryReservation res : partial) {
            itemRepository.findByProductIdForUpdate(res.getProductId())
                    .ifPresent(item -> {
                        item.release(res.getQuantity());
                        itemRepository.save(item);
                    });
            reservationRepository.delete(res);
        }
    }
}
