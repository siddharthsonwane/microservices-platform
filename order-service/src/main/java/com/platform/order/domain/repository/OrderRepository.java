package com.platform.order.domain.repository;

import com.platform.order.domain.entity.Order;
import com.platform.order.domain.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByCustomerId(String customerId, Pageable pageable);

    List<Order> findByStatus(OrderStatus status);

    Optional<Order> findBySagaId(String sagaId);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt < :before")
    List<Order> findStaleOrders(@Param("status") OrderStatus status,
                                @Param("before") LocalDateTime before);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") UUID id);

    boolean existsBySagaId(String sagaId);
}
