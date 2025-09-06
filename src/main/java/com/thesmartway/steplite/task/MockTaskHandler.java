package com.thesmartway.steplite.task;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class MockTaskHandler implements TaskHandler {
    
    @Override
    public TaskResult execute(Map<String, Object> input) {
        log.info("Executing mock task with input: {}", input);
        
        // Simulate processing delay if specified
        if (input.containsKey("sleepMs")) {
            try {
                long sleepMs = Long.parseLong(input.get("sleepMs").toString());
                TimeUnit.MILLISECONDS.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return TaskResult.failure("InterruptedException", "Task was interrupted");
            }
        }
        
        // Check if we should simulate an error
        if (input.containsKey("simulateError")) {
            String errorType = (String) input.getOrDefault("errorType", "MockError");
            String errorMessage = (String) input.getOrDefault("errorMessage", "Simulated error occurred");
            return TaskResult.failure(errorType, errorMessage);
        }
        
        // Pass through data and add some mock processing
        Map<String, Object> output = new HashMap<>(input);
        output.put("processedAt", System.currentTimeMillis());
        output.put("mockTaskId", "mock-" + System.currentTimeMillis());
        
        // Add mock processing result
        if (input.containsKey("orderId")) {
            output.put("orderStatus", "processed");
            output.put("processingTime", System.currentTimeMillis());
        }
        
        // Simulate conditional success/failure based on input
        if (input.containsKey("shouldFail") && Boolean.TRUE.equals(input.get("shouldFail"))) {
            return TaskResult.failure("ConditionalFailure", "Task failed due to shouldFail flag");
        }
        
        log.info("Mock task completed successfully with output: {}", output);
        return TaskResult.success(output);
    }
}
