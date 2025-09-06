package com.thesmartway.steplite.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResult {
    private Map<String, Object> output;
    private String errorType;
    private String errorMessage;
    private boolean success;
    
    public static TaskResult success(Map<String, Object> output) {
        return TaskResult.builder()
                .output(output)
                .success(true)
                .build();
    }
    
    public static TaskResult failure(String errorType, String errorMessage) {
        return TaskResult.builder()
                .errorType(errorType)
                .errorMessage(errorMessage)
                .success(false)
                .build();
    }
}
