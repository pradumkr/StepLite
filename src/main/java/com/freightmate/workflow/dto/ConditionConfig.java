package com.freightmate.workflow.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConditionConfig {
    private String operator; // booleanEquals, stringEquals, numericEquals, numericGreaterThan, numericLessThan
    private String variable; // JSONPath-like expression
    private Object value;
}
