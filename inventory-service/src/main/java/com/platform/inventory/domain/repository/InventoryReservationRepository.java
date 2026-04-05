package com.platform.inventory.domain.repository;

import com.platform.inventory.domain.entity.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {
    List<InventoryReservation> findByOrderId(String orderId);
    Optional<InventoryReservation> findBySagaIdAndProductId(String sagaId, String productId);
    boolean existsByOrderId(String orderId);
}
