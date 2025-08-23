package com.freightmate.workflow.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatchConfig {
    private String errorType; // TimeoutError, TaskError, etc.
    private String next;
}
