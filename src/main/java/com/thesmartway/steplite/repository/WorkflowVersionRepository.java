package com.thesmartway.steplite.repository;

import com.thesmartway.steplite.entity.WorkflowVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, Long> {
    
    List<WorkflowVersion> findByWorkflowId(Long workflowId);
    
    Optional<WorkflowVersion> findByWorkflowIdAndVersion(Long workflowId, String version);
    
    boolean existsByWorkflowIdAndVersion(Long workflowId, String version);
    
    List<WorkflowVersion> findByWorkflowIdOrderByVersionDesc(Long workflowId);
    
    Optional<WorkflowVersion> findFirstByWorkflowIdOrderByVersionDesc(Long workflowId);
    
    @Query("SELECT wv FROM WorkflowVersion wv WHERE wv.workflow.id = :workflowId AND wv.isActive = true")
    Optional<WorkflowVersion> findActiveVersionByWorkflowId(@Param("workflowId") Long workflowId);
}
