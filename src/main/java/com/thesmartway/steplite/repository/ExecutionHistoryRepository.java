package com.thesmartway.steplite.repository;

import com.thesmartway.steplite.entity.ExecutionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExecutionHistoryRepository extends JpaRepository<ExecutionHistory, Long> {
    
    List<ExecutionHistory> findByExecutionIdOrderByTimestampAsc(Long executionId);
    
    List<ExecutionHistory> findByExecutionIdAndStepNameOrderByTimestampAsc(Long executionId, String stepName);
}
