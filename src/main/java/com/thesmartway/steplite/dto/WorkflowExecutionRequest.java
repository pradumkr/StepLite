package com.thesmartway.steplite.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowExecutionRequest {
    private String workflowName;
    private String version; // Optional, defaults to latest
    private Map<String, Object> input;
}
