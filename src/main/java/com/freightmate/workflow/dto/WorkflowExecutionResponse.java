package com.freightmate.workflow.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
public class WorkflowExecutionResponse {
    private Long id;
    private String executionId;
    private String workflowName;
    private String version;
    private String status;
    private String currentState;
    private Map<String, Object> input;
    private Map<String, Object> output;
    private String errorMessage;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private Integer retryCount;
    private Integer maxRetries;
}
