package com.freightmate.workflow.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StateDefinition {
    private String type; // Task, Choice, Success, Fail
    private String next;
    private Map<String, Object> parameters;
    private RetryConfig retry;
    private List<CatchConfig> catchBlocks;
    private Integer timeout; // in seconds
    private List<ChoiceConfig> choices; // for Choice states
    private String defaultChoice; // for Choice states
}
