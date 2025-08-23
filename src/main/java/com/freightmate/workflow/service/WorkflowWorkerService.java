package com.freightmate.workflow.service;

import com.freightmate.workflow.dto.ExecutionStepResponse;
import com.freightmate.workflow.entity.*;
import com.freightmate.workflow.repository.*;
import com.freightmate.workflow.task.TaskRegistry;
import com.freightmate.workflow.task.TaskResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowWorkerService {
    
    @Value("${workflow.worker.batch-size:10}")
    private int batchSize;
    
    @Value("${workflow.worker.stuck-step-timeout-minutes:30}")
    private int stuckStepTimeoutMinutes;
    
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final ExecutionStepRepository executionStepRepository;
    private final ExecutionQueueRepository executionQueueRepository;
    private final ExecutionHistoryRepository executionHistoryRepository;
    private final TaskRegistry taskRegistry;
    private final ObjectMapper objectMapper;
    
    @Scheduled(fixedDelay = 1000) // Poll every second
    @Transactional
    public void processExecutionQueue() {
        try {
            // Get queued items with FOR UPDATE SKIP LOCKED
            List<ExecutionQueue> queuedItems = executionQueueRepository
                    .findQueuedItemsForProcessing(OffsetDateTime.now(), batchSize);
            
            if (queuedItems.isEmpty()) {
                return;
            }
            
            log.debug("Processing {} queued items", queuedItems.size());
            
            for (ExecutionQueue queueItem : queuedItems) {
                try {
                    processQueueItem(queueItem);
                } catch (Exception e) {
                    log.error("Error processing queue item: {}", queueItem.getId(), e);
                    // Mark step as failed and continue with next item
                    markStepAsFailed(queueItem.getExecutionId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error in workflow worker service", e);
        }
    }
    
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    @Transactional
    public void recoverStuckSteps() {
        try {
            OffsetDateTime timeoutThreshold = OffsetDateTime.now().minusMinutes(stuckStepTimeoutMinutes);
            
            List<ExecutionStep> stuckSteps = executionStepRepository
                    .findStuckSteps(timeoutThreshold);
            
            if (!stuckSteps.isEmpty()) {
                log.info("Recovering {} stuck steps", stuckSteps.size());
                
                for (ExecutionStep step : stuckSteps) {
                    recoverStuckStep(step);
                }
            }
        } catch (Exception e) {
            log.error("Error recovering stuck steps", e);
        }
    }
    
    private void processQueueItem(ExecutionQueue queueItem) {
        Long executionId = queueItem.getExecutionId();
        
        // Get the execution and current step
        WorkflowExecution execution = workflowExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
        
        ExecutionStep currentStep = executionStepRepository
                .findByExecutionIdAndStepName(executionId, execution.getCurrentState())
                .orElseThrow(() -> new IllegalArgumentException("Current step not found for execution: " + executionId));
        
        // Mark step as running
        currentStep.setStatus(ExecutionStep.StepStatus.RUNNING);
        currentStep.setStartedAt(OffsetDateTime.now());
        executionStepRepository.save(currentStep);
        
        // Add to history
        addExecutionHistory(executionId, currentStep.getStepName(), "STEP_STARTED", 
                Map.of("stepType", currentStep.getStepType()));
        
        try {
            // Execute the step based on its type
            TaskResult result = executeStep(currentStep, execution);
            
            if (result.isSuccess()) {
                // Step completed successfully
                currentStep.setStatus(ExecutionStep.StepStatus.COMPLETED);
                currentStep.setOutputData(result.getOutput());
                currentStep.setCompletedAt(OffsetDateTime.now());
                executionStepRepository.save(currentStep);
                
                addExecutionHistory(executionId, currentStep.getStepName(), "STEP_COMPLETED", 
                        Map.of("output", result.getOutput()));
                
                // Move to next state or complete execution
                moveToNextState(execution, currentStep, result.getOutput());
                
            } else {
                // Step failed
                currentStep.setStatus(ExecutionStep.StepStatus.FAILED);
                currentStep.setErrorMessage(result.getErrorMessage());
                currentStep.setCompletedAt(OffsetDateTime.now());
                executionStepRepository.save(currentStep);
                
                addExecutionHistory(executionId, currentStep.getStepName(), "STEP_FAILED", 
                        Map.of("errorType", result.getErrorType(), "errorMessage", result.getErrorMessage()));
                
                // Mark execution as failed
                execution.setStatus(WorkflowExecution.ExecutionStatus.FAILED);
                execution.setErrorMessage(result.getErrorMessage());
                execution.setCompletedAt(OffsetDateTime.now());
                workflowExecutionRepository.save(execution);
                
                addExecutionHistory(executionId, currentStep.getStepName(), "EXECUTION_FAILED", 
                        Map.of("errorMessage", result.getErrorMessage()));
            }
            
        } catch (Exception e) {
            log.error("Error executing step: {}", currentStep.getStepName(), e);
            
            // Mark step as failed
            currentStep.setStatus(ExecutionStep.StepStatus.FAILED);
            currentStep.setErrorMessage(e.getMessage());
            currentStep.setCompletedAt(OffsetDateTime.now());
            executionStepRepository.save(currentStep);
            
            addExecutionHistory(executionId, currentStep.getStepName(), "STEP_ERROR", 
                    Map.of("errorMessage", e.getMessage()));
            
            // Mark execution as failed
            execution.setStatus(WorkflowExecution.ExecutionStatus.FAILED);
            execution.setErrorMessage(e.getMessage());
            execution.setCompletedAt(OffsetDateTime.now());
            workflowExecutionRepository.save(execution);
        }
        
        // Remove from queue
        executionQueueRepository.delete(queueItem);
    }
    
    private TaskResult executeStep(ExecutionStep step, WorkflowExecution execution) {
        String stepType = step.getStepType();
        
        if ("Task".equals(stepType)) {
            // Execute task handler
            Map<String, Object> input = step.getInputData();
            return taskRegistry.getHandler("mock").execute(input);
            
        } else if ("Success".equals(stepType)) {
            // Success state - execution completed
            return TaskResult.success(step.getInputData());
            
        } else if ("Fail".equals(stepType)) {
            // Fail state - execution failed
            String errorMessage = (String) step.getInputData().getOrDefault("error", "Workflow failed");
            return TaskResult.failure("WorkflowFail", errorMessage);
            
        } else {
            throw new IllegalArgumentException("Unsupported step type: " + stepType);
        }
    }
    
    private void moveToNextState(WorkflowExecution execution, ExecutionStep currentStep, Map<String, Object> stepOutput) {
        // Parse workflow definition to find next state
        Map<String, Object> definition = parseWorkflowDefinition(
                execution.getWorkflowVersion().getDefinitionJsonb());
        Map<String, Object> states = (Map<String, Object>) definition.get("states");
        Map<String, Object> currentStateDef = (Map<String, Object>) states.get(currentStep.getStepName());
        
        String nextState = (String) currentStateDef.get("next");
        
        if (nextState != null && states.containsKey(nextState)) {
            // Create next step
            ExecutionStep nextStep = ExecutionStep.builder()
                    .executionId(execution.getId())
                    .stepName(nextState)
                    .stepType(getStepType(states.get(nextState)))
                    .status(ExecutionStep.StepStatus.PENDING)
                    .inputData(mergeInputWithOutput(stepOutput, currentStep.getInputData()))
                    .retryCount(0)
                    .maxRetries(3)
                    .build();
            
            nextStep = executionStepRepository.save(nextStep);
            
            // Update execution current state
            execution.setCurrentState(nextState);
            workflowExecutionRepository.save(execution);
            
            // Enqueue next step
            ExecutionQueue nextQueueItem = ExecutionQueue.builder()
                    .executionId(execution.getId())
                    .priority(0)
                    .scheduledAt(OffsetDateTime.now())
                    .status(ExecutionQueue.QueueStatus.QUEUED)
                    .build();
            
            executionQueueRepository.save(nextQueueItem);
            
            addExecutionHistory(execution.getId(), nextState, "NEXT_STATE_QUEUED", 
                    Map.of("previousState", currentStep.getStepName()));
            
        } else {
            // No next state - execution completed successfully
            execution.setStatus(WorkflowExecution.ExecutionStatus.COMPLETED);
            execution.setOutputData(stepOutput);
            execution.setCompletedAt(OffsetDateTime.now());
            workflowExecutionRepository.save(execution);
            
            addExecutionHistory(execution.getId(), currentStep.getStepName(), "EXECUTION_COMPLETED", 
                    Map.of("finalOutput", stepOutput));
        }
    }
    
    private void markStepAsFailed(Long executionId, String errorMessage) {
        try {
            // Find the current step for this execution
            WorkflowExecution execution = workflowExecutionRepository.findById(executionId).orElse(null);
            if (execution != null && execution.getCurrentState() != null) {
                ExecutionStep step = executionStepRepository
                        .findByExecutionIdAndStepName(executionId, execution.getCurrentState())
                        .orElse(null);
                
                if (step != null) {
                    step.setStatus(ExecutionStep.StepStatus.FAILED);
                    step.setErrorMessage(errorMessage);
                    step.setCompletedAt(OffsetDateTime.now());
                    executionStepRepository.save(step);
                    
                    // Mark execution as failed
                    execution.setStatus(WorkflowExecution.ExecutionStatus.FAILED);
                    execution.setErrorMessage(errorMessage);
                    execution.setCompletedAt(OffsetDateTime.now());
                    workflowExecutionRepository.save(execution);
                    
                    addExecutionHistory(executionId, step.getStepName(), "STEP_FAILED", 
                            Map.of("errorMessage", errorMessage));
                }
            }
        } catch (Exception e) {
            log.error("Error marking step as failed for execution: {}", executionId, e);
        }
    }
    
    private void recoverStuckStep(ExecutionStep step) {
        step.setStatus(ExecutionStep.StepStatus.PENDING);
        step.setStartedAt(null);
        step.setCompletedAt(null);
        executionStepRepository.save(step);
        
        // Re-enqueue
        ExecutionQueue queueItem = ExecutionQueue.builder()
                .executionId(step.getExecutionId())
                .priority(0)
                .scheduledAt(OffsetDateTime.now())
                .status(ExecutionQueue.QueueStatus.QUEUED)
                .build();
        
        executionQueueRepository.save(queueItem);
        
        addExecutionHistory(step.getExecutionId(), step.getStepName(), "STEP_RECOVERED", 
                Map.of("reason", "Stuck step recovery"));
        
        log.info("Recovered stuck step: {} for execution: {}", step.getStepName(), step.getExecutionId());
    }
    
    private Map<String, Object> mergeInputWithOutput(Map<String, Object> output, Map<String, Object> input) {
        Map<String, Object> merged = new HashMap<>(input);
        if (output != null) {
            merged.putAll(output); // Output overrides input (shallow merge)
        }
        return merged;
    }
    
    private String getStepType(Object state) {
        if (state instanceof Map) {
            Map<String, Object> stateMap = (Map<String, Object>) state;
            return (String) stateMap.getOrDefault("type", "Task");
        }
        return "Task";
    }
    
    private Map<String, Object> parseWorkflowDefinition(String definitionJsonb) {
        try {
            return objectMapper.readValue(definitionJsonb, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid workflow definition JSON", e);
        }
    }
    
    private String convertToJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Error converting object to JSON", e);
            return "{}";
        }
    }
    
    private Map<String, Object> parseJsonString(String jsonString) {
        try {
            if (jsonString == null || jsonString.trim().isEmpty()) {
                return new HashMap<>();
            }
            return objectMapper.readValue(jsonString, Map.class);
        } catch (Exception e) {
            log.error("Error parsing JSON string", e);
            return new HashMap<>();
        }
    }
    
    private void addExecutionHistory(Long executionId, String stepName, String eventType, Map<String, Object> eventData) {
        ExecutionHistory history = ExecutionHistory.builder()
                .executionId(executionId)
                .stepName(stepName)
                .eventType(eventType)
                .eventData(eventData)
                .build();
        
        executionHistoryRepository.save(history);
    }
}
