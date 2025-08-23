package com.freightmate.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionHistoryResponse {
    private Long id;
    private String stepName;
    private String eventType;
    private Map<String, Object> eventData;
    private OffsetDateTime timestamp;
}
