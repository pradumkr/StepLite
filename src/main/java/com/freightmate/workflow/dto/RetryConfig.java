package com.freightmate.workflow.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetryConfig {
    private Integer maxAttempts = 3;
    private Double backoffMultiplier = 2.0;
    private Long initialIntervalMs = 1000L;
}
