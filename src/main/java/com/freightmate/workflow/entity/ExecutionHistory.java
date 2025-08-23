package com.freightmate.workflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "execution_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    private WorkflowExecution execution;
    
    @Column(name = "step_name")
    private String stepName;
    
    @Column(name = "event_type", nullable = false)
    private String eventType;
    
    @Column(name = "event_data", columnDefinition = "jsonb")
    private String eventData;
    
    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private OffsetDateTime timestamp;
}
