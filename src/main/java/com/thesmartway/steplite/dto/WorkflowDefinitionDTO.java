package com.thesmartway.steplite.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowDefinitionDTO {
    
    @NotBlank(message = "Workflow name is required")
    private String name;
    
    @NotBlank(message = "Workflow version is required")
    private String version;
    
    private String description;
    
    @NotBlank(message = "Start state is required")
    @JsonProperty("startAt")
    private String startAt;
    
    @NotNull(message = "Workflow states are required")
    private Map<String, StateDefinition> states;
}
