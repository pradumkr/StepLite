package com.thesmartway.steplite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.thesmartway.steplite.dto.ConditionConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;

/**
 * Service for evaluating Choice state conditions
 */
@Service
@Slf4j
public class ConditionEvaluatorService {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Evaluate a condition against the current execution context
     */
    public boolean evaluateCondition(ConditionConfig condition, Map<String, Object> context) {
        try {
            String operator = condition.getOperator();
            String variable = condition.getVariable();
            Object value = condition.getValue();
            
            log.info("🔍 Evaluating condition: operator={}, variable={}, expectedValue={}", operator, variable, value);
            log.info("🔍 Context data: {}", context);
            
            if (operator == null || variable == null) {
                log.warn("❌ Invalid condition: missing operator or variable");
                return false;
            }
            
            Object contextValue = extractValue(context, variable);
            log.info("🔍 Extracted value from context: {} -> {}", variable, contextValue);
            
            boolean result = false;
            switch (operator) {
                case "booleanEquals":
                    result = booleanEquals(contextValue, value);
                    break;
                case "stringEquals":
                    result = stringEquals(contextValue, value);
                    break;
                case "numericEquals":
                    result = numericEquals(contextValue, value);
                    break;
                case "numericGreaterThan":
                    result = numericGreaterThan(contextValue, value);
                    break;
                case "numericLessThan":
                    result = numericLessThan(contextValue, value);
                    break;
                default:
                    log.warn("❌ Unsupported operator: {}", operator);
                    return false;
            }
            
            log.info("🔍 Condition evaluation result: {} {} {} = {}", variable, operator, value, result);
            return result;
            
        } catch (Exception e) {
            log.error("❌ Error evaluating condition: {}", condition, e);
            return false;
        }
    }
    
    private Object extractValue(Map<String, Object> context, String variable) {
        // Simple JSONPath-like extraction (supports dot notation)
        log.info("🔍 Extracting variable: {} from context: {}", variable, context);
        
        // Remove the leading "$." if present
        if (variable.startsWith("$.")) {
            variable = variable.substring(2);
        }
        
        String[] parts = variable.split("\\.");
        log.info("🔍 Split parts: {}", Arrays.toString(parts));
        
        Object current = context;
        log.info("🔍 Starting with context: {}", current);
        
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            log.info("🔍 Processing part {}: '{}'", i + 1, part);
            
            if (current instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> currentMap = (Map<String, Object>) current;
                log.info("🔍 Current is Map: {}", currentMap);
                
                current = currentMap.get(part);
                log.info("🔍 After getting '{}': {}", part, current);
                
                if (current == null) {
                    log.warn("🔍 ❌ Part '{}' returned null, stopping extraction", part);
                    return null;
                }
            } else {
                log.warn("🔍 ❌ Current is not a Map: {} (type: {})", current, current.getClass().getSimpleName());
                return null;
            }
        }
        
        log.info("🔍 ✅ Final extracted value: {}", current);
        return current;
    }
    
    private boolean booleanEquals(Object contextValue, Object expectedValue) {
        if (contextValue == null || expectedValue == null) {
            return contextValue == expectedValue;
        }
        
        if (contextValue instanceof Boolean && expectedValue instanceof Boolean) {
            return contextValue.equals(expectedValue);
        }
        
        // Convert to boolean if possible
        boolean contextBool = Boolean.parseBoolean(String.valueOf(contextValue));
        boolean expectedBool = Boolean.parseBoolean(String.valueOf(expectedValue));
        return contextBool == expectedBool;
    }
    
    private boolean stringEquals(Object contextValue, Object expectedValue) {
        if (contextValue == null || expectedValue == null) {
            return contextValue == expectedValue;
        }
        
        return String.valueOf(contextValue).equals(String.valueOf(expectedValue));
    }
    
    private boolean numericEquals(Object contextValue, Object expectedValue) {
        if (contextValue == null || expectedValue == null) {
            return false;
        }
        
        try {
            double contextNum = Double.parseDouble(String.valueOf(contextValue));
            double expectedNum = Double.parseDouble(String.valueOf(expectedValue));
            return Math.abs(contextNum - expectedNum) < 0.000001; // Small epsilon for floating point comparison
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private boolean numericGreaterThan(Object contextValue, Object expectedValue) {
        if (contextValue == null || expectedValue == null) {
            return false;
        }
        
        try {
            double contextNum = Double.parseDouble(String.valueOf(contextValue));
            double expectedNum = Double.parseDouble(String.valueOf(expectedValue));
            return contextNum > expectedNum;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private boolean numericLessThan(Object contextValue, Object expectedValue) {
        if (contextValue == null || expectedValue == null) {
            return false;
        }
        
        try {
            double contextNum = Double.parseDouble(String.valueOf(contextValue));
            double expectedNum = Double.parseDouble(String.valueOf(expectedValue));
            return contextNum < expectedNum;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
