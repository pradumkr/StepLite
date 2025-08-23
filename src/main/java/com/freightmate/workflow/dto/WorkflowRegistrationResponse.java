package com.freightmate.workflow.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkflowRegistrationResponse {
    private Long workflowVersionId;
    private String message;
}