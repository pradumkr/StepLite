package com.freightmate.workflow.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowResponseDTO {
    
    private Long id;
    private String name;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<WorkflowVersionDTO> versions;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WorkflowVersionDTO {
        private Long id;
        private String version;
        private String definitionJsonb;
        private Boolean isActive;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
    }
}
