package com.thesmartway.steplite.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStepResponse {
    private Long id;
    private String stepName;
    private String stepType;
    private String status;
    private Map<String, Object> input;
    private Map<String, Object> output;
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetries;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private List<ExecutionHistoryResponse> history;
}
