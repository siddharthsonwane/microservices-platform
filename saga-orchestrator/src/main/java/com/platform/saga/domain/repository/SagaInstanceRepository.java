package com.platform.saga.domain.repository;

import com.platform.saga.domain.entity.SagaInstance;
import com.platform.saga.domain.entity.SagaStatus;
import com.platform.saga.domain.entity.SagaStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, String> {

    Optional<SagaInstance> findByOrderId(String orderId);

    List<SagaInstance> findByStatus(SagaStatus status);

    @Query("SELECT s FROM SagaInstance s WHERE s.status IN ('STARTED','IN_PROGRESS') " +
           "AND s.updatedAt < :threshold")
    List<SagaInstance> findStaleSagas(@Param("threshold") LocalDateTime threshold);

    boolean existsByOrderId(String orderId);
}
