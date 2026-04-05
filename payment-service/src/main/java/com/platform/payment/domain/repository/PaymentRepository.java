package com.platform.payment.domain.repository;

import com.platform.payment.domain.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(String orderId);
    Optional<Payment> findBySagaId(String sagaId);
    boolean existsByOrderId(String orderId);
}
