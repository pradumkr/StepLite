package com.thesmartway.steplite.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkflowRegistrationResponse {
    private Long workflowVersionId;
    private String message;
}