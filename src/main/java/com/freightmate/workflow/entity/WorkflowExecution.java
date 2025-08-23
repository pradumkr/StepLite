package com.freightmate.workflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "workflow_executions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowExecution {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_version_id", nullable = false)
    private WorkflowVersion workflowVersion;
    
    @Column(name = "execution_id", nullable = false, unique = true)
    private String executionId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExecutionStatus status = ExecutionStatus.RUNNING;
    
    @Column(name = "current_state")
    private String currentState;
    
    @Column(name = "input_data", columnDefinition = "jsonb")
    private String inputData;
    
    @Column(name = "output_data", columnDefinition = "jsonb")
    private String outputData;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;
    
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    
    public enum ExecutionStatus {
        RUNNING, COMPLETED, FAILED, CANCELLED
    }
}
