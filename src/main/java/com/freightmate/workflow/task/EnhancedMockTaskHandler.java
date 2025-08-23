package com.freightmate.workflow.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Component("enhancedMock")
@Slf4j
public class EnhancedMockTaskHandler implements TaskHandler {
    
    private final Random random = new Random();
    
    @Override
    public TaskResult execute(Map<String, Object> input) {
        String taskType = (String) input.get("taskType");
        log.info("Executing enhanced mock task: {}", taskType);
        
        switch (taskType) {
            case "orderService.validate":
                return handleOrderValidation(input);
            case "inventoryService.check":
                return handleInventoryCheck(input);
            case "orderService.process":
                return handleOrderProcessing(input);
            default:
                return TaskResult.success(input);
        }
    }
    
    private TaskResult handleOrderValidation(Map<String, Object> input) {
        String customerName = (String) input.get("customerName");
        if (customerName == null || customerName.trim().isEmpty()) {
            return TaskResult.failure("ValidationError", "Customer name is required");
        }
        
        Map<String, Object> output = new HashMap<>(input);
        output.put("validated", true);
        return TaskResult.success(output);
    }
    
    private TaskResult handleInventoryCheck(Map<String, Object> input) {
        boolean inStock = random.nextBoolean();
        Map<String, Object> output = new HashMap<>(input);
        output.put("inStock", inStock);
        return TaskResult.success(output);
    }
    
    private TaskResult handleOrderProcessing(Map<String, Object> input) {
        Double amount = (Double) input.get("amount");
        if (amount != null && amount > 200) {
            return TaskResult.failure("PaymentError", "Payment failed for high amount");
        }
        
        Map<String, Object> output = new HashMap<>(input);
        output.put("processed", true);
        return TaskResult.success(output);
    }
}
