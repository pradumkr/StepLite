package com.freightmate.workflow.repository;

import com.freightmate.workflow.entity.WorkflowExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Long> {
    
    Optional<WorkflowExecution> findByExecutionId(String executionId);
    
    @Query("SELECT we FROM WorkflowExecution we " +
           "JOIN FETCH we.workflowVersion wv " +
           "JOIN FETCH wv.workflow w " +
           "WHERE we.id = :id")
    Optional<WorkflowExecution> findByIdWithDetails(@Param("id") Long id);
    
    @Query("SELECT we FROM WorkflowExecution we " +
           "JOIN FETCH we.workflowVersion wv " +
           "JOIN FETCH wv.workflow w " +
           "WHERE (:statuses IS NULL OR we.status IN :statuses) " +
           "AND (:workflowName IS NULL OR w.name = :workflowName) " +
           "AND (:startDate IS NULL OR we.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR we.createdAt <= :endDate) " +
           "ORDER BY " +
           "CASE WHEN :sortBy = 'createdAt' AND :sortOrder = 'ASC' THEN we.createdAt END ASC, " +
           "CASE WHEN :sortBy = 'createdAt' AND :sortOrder = 'DESC' THEN we.createdAt END DESC, " +
           "CASE WHEN :sortBy = 'status' AND :sortOrder = 'ASC' THEN we.status END ASC, " +
           "CASE WHEN :sortBy = 'status' AND :sortOrder = 'DESC' THEN we.status END DESC " +
           "LIMIT :limit OFFSET :offset")
    List<WorkflowExecution> findByFilters(
            @Param("statuses") List<String> statuses,
            @Param("workflowName") String workflowName,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            @Param("sortBy") String sortBy,
            @Param("sortOrder") String sortOrder,
            @Param("limit") Integer limit,
            @Param("offset") Integer offset
    );
}
