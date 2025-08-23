package com.freightmate.workflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import com.vladmihalcea.hibernate.type.json.JsonType;

import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "execution_steps")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionStep {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "execution_id", nullable = false)
    private Long executionId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", insertable = false, updatable = false)
    private WorkflowExecution execution;
    
    @Column(name = "step_name", nullable = false)
    private String stepName;
    
    @Column(name = "step_type", nullable = false)
    private String stepType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StepStatus status = StepStatus.PENDING;
    
    @Column(name = "input_data", columnDefinition = "jsonb")
    @Type(JsonType.class)
    private Map<String, Object> inputData;
    
    @Column(name = "output_data", columnDefinition = "jsonb")
    @Type(JsonType.class)
    private Map<String, Object> outputData;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @Column(name = "started_at")
    private OffsetDateTime startedAt;
    
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
    
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;
    
    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 3;
    
    @Column(name = "backoff_multiplier")
    private Double backoffMultiplier = 2.0;
    
    @Column(name = "initial_interval_ms")
    private Long initialIntervalMs = 1000L;
    
    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;
    
    @Column(name = "error_type")
    private String errorType;
    
    @Column(name = "run_after_ts")
    private OffsetDateTime runAfterTs;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    
    public enum StepStatus {
        PENDING, RUNNING, COMPLETED, FAILED, RETRYING
    }
}
