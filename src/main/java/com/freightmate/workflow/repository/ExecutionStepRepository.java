package com.freightmate.workflow.repository;

import com.freightmate.workflow.entity.ExecutionStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExecutionStepRepository extends JpaRepository<ExecutionStep, Long> {
    
    List<ExecutionStep> findByExecutionId(Long executionId);
    
    Optional<ExecutionStep> findByExecutionIdAndStepName(Long executionId, String stepName);
    
    @Query("SELECT es FROM ExecutionStep es " +
           "WHERE es.status = 'RUNNING' " +
           "AND es.startedAt < :timeoutThreshold")
    List<ExecutionStep> findStuckSteps(@Param("timeoutThreshold") OffsetDateTime timeoutThreshold);
}
