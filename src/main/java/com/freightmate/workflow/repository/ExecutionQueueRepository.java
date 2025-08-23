package com.freightmate.workflow.repository;

import com.freightmate.workflow.entity.ExecutionQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExecutionQueueRepository extends JpaRepository<ExecutionQueue, Long> {
    
    List<ExecutionQueue> findByStatusOrderByPriorityDescScheduledAtAsc(String status);
    
    @Query(value = """
        SELECT eq.* FROM execution_queue eq 
        WHERE eq.status = :status 
        AND eq.scheduled_at <= :currentTime
        ORDER BY eq.priority DESC, eq.scheduled_at ASC 
        LIMIT :limit 
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<ExecutionQueue> findNextExecutionsForUpdate(
        @Param("status") String status,
        @Param("currentTime") OffsetDateTime currentTime,
        @Param("limit") int limit
    );
    
    Optional<ExecutionQueue> findByExecutionId(Long executionId);
    
    @Query("SELECT eq FROM ExecutionQueue eq WHERE eq.execution.id = :executionId")
    Optional<ExecutionQueue> findByExecutionIdWithExecution(@Param("executionId") Long executionId);
}
