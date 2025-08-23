package com.freightmate.workflow.repository;

import com.freightmate.workflow.entity.WorkflowExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Long> {
    
    Optional<WorkflowExecution> findByExecutionId(String executionId);
    
    @Query("SELECT we FROM WorkflowExecution we " +
           "JOIN FETCH we.workflowVersion wv " +
           "JOIN FETCH wv.workflow w " +
           "WHERE we.id = :id")
    Optional<WorkflowExecution> findByIdWithDetails(@Param("id") Long id);
}
