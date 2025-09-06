package com.thesmartway.steplite.service;

import com.thesmartway.steplite.dto.ExecutionStepResponse;
import com.thesmartway.steplite.dto.ConditionConfig;
import com.thesmartway.steplite.entity.*;
import com.thesmartway.steplite.repository.*;
import com.thesmartway.steplite.task.TaskRegistry;
import com.thesmartway.steplite.task.TaskResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
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
    private final ConditionEvaluatorService conditionEvaluatorService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private final AtomicInteger queuedItemsProcessed = new AtomicInteger(0);
    private final AtomicInteger stuckStepsRecovered = new AtomicInteger(0);
    private final AtomicInteger waitStepsProcessed = new AtomicInteger(0);
    
    private Counter workflowExecutionCounter;
    private Counter workflowStepCompletedCounter;
    private Counter workflowStepFailedCounter;
    private Counter workflowExecutionFailedCounter;
    private Counter workflowExecutionCancelledCounter;
    
    private Timer workflowExecutionTimer;
    private Timer workflowStepTimer;
    
    public WorkflowWorkerService(
            WorkflowExecutionRepository workflowExecutionRepository,
            ExecutionStepRepository executionStepRepository,
            ExecutionQueueRepository executionQueueRepository,
            ExecutionHistoryRepository executionHistoryRepository,
            TaskRegistry taskRegistry,
            ConditionEvaluatorService conditionEvaluatorService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.executionStepRepository = executionStepRepository;
        this.executionQueueRepository = executionQueueRepository;
        this.executionHistoryRepository = executionHistoryRepository;
        this.taskRegistry = taskRegistry;
        this.conditionEvaluatorService = conditionEvaluatorService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }
    
    @PostConstruct
    public void initializeMetrics() {
        workflowExecutionCounter = meterRegistry.counter("workflow_executions", "status", "running");
        workflowStepCompletedCounter = meterRegistry.counter("workflow_steps", "event", "completed");
        workflowStepFailedCounter = meterRegistry.counter("workflow_steps", "event", "failed");
        workflowExecutionFailedCounter = meterRegistry.counter("workflow_executions", "status", "failed");
        workflowExecutionCancelledCounter = meterRegistry.counter("workflow_executions", "status", "cancelled");
        
        workflowExecutionTimer = meterRegistry.timer("workflow_execution_duration_seconds");
        workflowStepTimer = meterRegistry.timer("workflow_step_duration_seconds");
        
        // Register gauges with proper method signatures
        meterRegistry.gauge("workflow_queued_items", queuedItemsProcessed);
        meterRegistry.gauge("workflow_stuck_steps", stuckStepsRecovered);
        meterRegistry.gauge("workflow_wait_steps", waitStepsProcessed);
    }
    
    @Scheduled(fixedDelay = 1000) // Poll every second
    @Transactional
    public void processExecutionQueue() {
        try {
            // Get queued items with FOR UPDATE SKIP LOCKED, respecting run_after_ts
            List<ExecutionQueue> queuedItems = executionQueueRepository
                    .findQueuedItemsForProcessing(OffsetDateTime.now(), batchSize);
            
            if (queuedItems.isEmpty()) {
                return;
            }
            
            log.debug("Processing {} queued items", queuedItems.size());
            
            for (ExecutionQueue queueItem : queuedItems) {
                try {
                    // Check if item is ready to run
                    if (queueItem.getScheduledAt().isAfter(OffsetDateTime.now())) {
                        log.debug("Queue item {} not ready yet, scheduled for: {}", 
                                queueItem.getId(), queueItem.getScheduledAt());
                        continue;
                    }
                    
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
    
    @Scheduled(fixedDelay = 10000) // Every 10 seconds
    @Transactional
    public void processWaitStates() {
        try {
            // Find Wait steps that are ready to proceed
            List<ExecutionStep> readyWaitSteps = executionStepRepository
                    .findWaitStepsReadyToProceed(OffsetDateTime.now());
            
            if (!readyWaitSteps.isEmpty()) {
                log.info("Processing {} Wait steps ready to proceed", readyWaitSteps.size());
                
                for (ExecutionStep step : readyWaitSteps) {
                    processWaitStep(step);
                }
            }
        } catch (Exception e) {
            log.error("Error processing Wait states", e);
        }
    }
    
    private void processQueueItem(ExecutionQueue queueItem) {
        Long executionId = queueItem.getExecutionId();
        
        // Get the execution and current step
        WorkflowExecution execution = workflowExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
        
        // Check if execution is cancelled
        if (execution.getStatus() == WorkflowExecution.ExecutionStatus.CANCELLED) {
            log.info("Skipping cancelled execution: {}", executionId);
            executionQueueRepository.delete(queueItem);
            return;
        }
        
        ExecutionStep currentStep = executionStepRepository
                .findByExecutionIdAndStepName(executionId, execution.getCurrentState())
                .orElseThrow(() -> new IllegalArgumentException("Current step not found for execution: " + executionId));
        
        if ("Wait".equals(currentStep.getStepType())) {
            log.debug("Skipping Wait step {} in main queue processor - will be handled by wait state processor", 
                    currentStep.getStepName());
            return;
        }
        
                // Mark step as running
        currentStep.setStatus(ExecutionStep.StepStatus.RUNNING);
        currentStep.setStartedAt(OffsetDateTime.now());
        executionStepRepository.save(currentStep);
        
        // Add to history
        addExecutionHistory(executionId, currentStep.getStepName(), "STEP_STARTED", 
                Map.of("stepType", currentStep.getStepType()));
        
        // Record step start time for metrics
        Timer.Sample stepTimer = Timer.start(meterRegistry);
        
        try {
            // Execute the step based on its type
            TaskResult result = executeStep(currentStep, execution);
            
            if (result.isSuccess()) {
                // Step completed successfully
                currentStep.setStatus(ExecutionStep.StepStatus.COMPLETED);
                currentStep.setOutputData(result.getOutput());
                currentStep.setCompletedAt(OffsetDateTime.now());
                executionStepRepository.save(currentStep);
                
                // Record metrics
                workflowStepCompletedCounter.increment();
                stepTimer.stop(workflowStepTimer);
                
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
                
                // Record metrics
                workflowStepFailedCounter.increment();
                stepTimer.stop(workflowStepTimer);
                
                addExecutionHistory(executionId, currentStep.getStepName(), "STEP_FAILED", 
                        Map.of("errorType", result.getErrorType(), "errorMessage", result.getErrorMessage()));
                
                // Mark execution as failed
                execution.setStatus(WorkflowExecution.ExecutionStatus.FAILED);
                execution.setErrorMessage(result.getErrorMessage());
                execution.setCompletedAt(OffsetDateTime.now());
                workflowExecutionRepository.save(execution);
                
                // Record metrics
                workflowExecutionFailedCounter.increment();
                
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
            
            // Record metrics
            workflowStepFailedCounter.increment();
            stepTimer.stop(workflowStepTimer);
            
            addExecutionHistory(executionId, currentStep.getStepName(), "STEP_ERROR", 
                    Map.of("errorMessage", e.getMessage()));
            
            // Mark execution as failed
            execution.setStatus(WorkflowExecution.ExecutionStatus.FAILED);
            execution.setErrorMessage(e.getMessage());
            execution.setCompletedAt(OffsetDateTime.now());
            workflowExecutionRepository.save(execution);
            
            // Record metrics
            workflowExecutionFailedCounter.increment();
        }
        
        // Remove from queue
        executionQueueRepository.delete(queueItem);
        
        // Update processed count
        queuedItemsProcessed.incrementAndGet();
    }
    
    private TaskResult executeStep(ExecutionStep step, WorkflowExecution execution) {
        String stepType = step.getStepType();
        
        if ("Task".equals(stepType)) {
            // Execute task handler
            Map<String, Object> input = step.getInputData();
            return taskRegistry.getHandler("mock").execute(input);
            
        } else if ("Choice".equals(stepType)) {
            // Choice state - evaluate conditions
            return executeChoiceStep(step, execution);
            
        } else if ("Wait".equals(stepType)) {
            // Wait state - handle timing
            return executeWaitStep(step, execution);
            
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
    
    private TaskResult executeChoiceStep(ExecutionStep step, WorkflowExecution execution) {
        log.info("🎯 Executing Choice step: {} for execution: {}", step.getStepName(), execution.getId());
        
        // Parse workflow definition to get choice configuration
        Map<String, Object> definition = parseWorkflowDefinition(
                execution.getWorkflowVersion().getDefinitionJsonb());
        Map<String, Object> states = (Map<String, Object>) definition.get("states");
        Map<String, Object> currentStateDef = (Map<String, Object>) states.get(step.getStepName());
        
        log.info("🎯 Current state definition: {}", currentStateDef);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) currentStateDef.get("choices");
        String defaultChoice = (String) currentStateDef.get("defaultChoice");
        
        log.info("🎯 Found {} choices, defaultChoice: {}", choices != null ? choices.size() : 0, defaultChoice);
        log.info("🎯 Step input data: {}", step.getInputData());
        
        if (choices != null) {
            for (int i = 0; i < choices.size(); i++) {
                Map<String, Object> choice = choices.get(i);
                log.info("🎯 Evaluating choice {}: {}", i + 1, choice);
                
                Map<String, Object> conditionMap = (Map<String, Object>) choice.get("condition");
                if (conditionMap != null) {
                    log.info("🎯 Condition map: {}", conditionMap);
                    
                    ConditionConfig condition = mapToConditionConfig(conditionMap);
                    log.info("🎯 Mapped condition: operator={}, variable={}, value={}", 
                            condition.getOperator(), condition.getVariable(), condition.getValue());
                    
                    boolean conditionResult = conditionEvaluatorService.evaluateCondition(condition, step.getInputData());
                    log.info("🎯 Condition evaluation result: {}", conditionResult);
                    
                    if (conditionResult) {
                        String nextState = (String) choice.get("next");
                        log.info("🎯 ✅ Condition matched! Next state: {}", nextState);
                        if (nextState != null) {
                            return TaskResult.success(Map.of("nextState", nextState));
                        }
                    } else {
                        log.info("🎯 ❌ Condition did not match, trying next choice");
                    }
                } else {
                    log.warn("🎯 ❌ Choice {} has no condition", i + 1);
                }
            }
        }
        
        // Use default choice if no conditions match
        if (defaultChoice != null) {
            log.info("🎯 Using default choice: {}", defaultChoice);
            return TaskResult.success(Map.of("nextState", defaultChoice));
        }
        
        log.error("🎯 ❌ No matching choice found and no default specified!");
        return TaskResult.failure("ChoiceError", "No matching choice found and no default specified");
    }
    
    private TaskResult executeWaitStep(ExecutionStep step, WorkflowExecution execution) {
        log.info("⏰ Executing Wait step: {} for execution: {}", step.getStepName(), execution.getId());
        
        // Parse workflow definition to get wait configuration
        Map<String, Object> definition = parseWorkflowDefinition(
                execution.getWorkflowVersion().getDefinitionJsonb());
        Map<String, Object> states = (Map<String, Object>) definition.get("states");
        Map<String, Object> currentStateDef = (Map<String, Object>) states.get(step.getStepName());
        
        // Check if step should run now or wait
        OffsetDateTime runAfterTs = step.getRunAfterTs();
        if (runAfterTs != null && runAfterTs.isAfter(OffsetDateTime.now())) {
            // Still waiting
            log.info("⏰ Step {} still waiting, run after: {}", step.getStepName(), runAfterTs);
            return TaskResult.failure("StillWaiting", "Step is still waiting");
        }
        
        // Step is ready to proceed
        log.info("⏰ Wait step {} completed, proceeding to next state", step.getStepName());
        return TaskResult.success(Map.of("waitCompleted", true));
    }
    
    private OffsetDateTime calculateWaitTime(Object stateDef) {
        if (stateDef instanceof Map) {
            Map<String, Object> stateMap = (Map<String, Object>) stateDef;
            
            // Check for seconds configuration
            Object secondsObj = stateMap.get("seconds");
            if (secondsObj instanceof Number) {
                int seconds = ((Number) secondsObj).intValue();
                return OffsetDateTime.now().plusSeconds(seconds);
            }
            
            // Check for timestamp configuration
            Object timestampObj = stateMap.get("timestamp");
            if (timestampObj instanceof String) {
                try {
                    return OffsetDateTime.parse((String) timestampObj);
                } catch (Exception e) {
                    log.error("Error parsing timestamp: {}", timestampObj, e);
                    throw new IllegalArgumentException("Invalid timestamp format: " + timestampObj);
                }
            }
        }
        
        // Default to immediate execution
        return OffsetDateTime.now();
    }
    
    private void moveToNextState(WorkflowExecution execution, ExecutionStep currentStep, Map<String, Object> stepOutput) {
        // Check if execution is cancelled
        if (execution.getStatus() == WorkflowExecution.ExecutionStatus.CANCELLED) {
            log.info("Execution {} is cancelled, not scheduling next states", execution.getId());
            return;
        }
        
        // Parse workflow definition to find next state
        Map<String, Object> definition = parseWorkflowDefinition(
                execution.getWorkflowVersion().getDefinitionJsonb());
        Map<String, Object> states = (Map<String, Object>) definition.get("states");
        Map<String, Object> currentStateDef = (Map<String, Object>) states.get(currentStep.getStepName());
        
        String nextState = null;
        
        // Handle Choice states
        if ("Choice".equals(currentStep.getStepType())) {
            Object nextStateObj = stepOutput.get("nextState");
            if (nextStateObj instanceof String) {
                nextState = (String) nextStateObj;
            }
        } else {
            // Regular states
            nextState = (String) currentStateDef.get("next");
        }
        
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
            
            // Handle Wait state configuration
            if ("Wait".equals(getStepType(states.get(nextState)))) {
                nextStep.setStatus(ExecutionStep.StepStatus.WAITING);
                OffsetDateTime runAfterTs = calculateWaitTime(states.get(nextState));
                nextStep.setRunAfterTs(runAfterTs);
                log.info("⏰ Setting Wait step {} to run after: {}", nextState, runAfterTs);
            }
            
            nextStep = executionStepRepository.save(nextStep);
            
            // Update execution current state
            execution.setCurrentState(nextState);
            workflowExecutionRepository.save(execution);
            
            // Enqueue next step
            ExecutionQueue nextQueueItem = ExecutionQueue.builder()
                    .executionId(execution.getId())
                    .priority(0)
                    .scheduledAt(nextStep.getRunAfterTs() != null ? nextStep.getRunAfterTs() : OffsetDateTime.now())
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
        
        // Record metrics
        stuckStepsRecovered.incrementAndGet();
        
        log.info("Recovered stuck step: {} for execution: {}", step.getStepName(), step.getExecutionId());
    }
    
    private void processWaitStep(ExecutionStep step) {
        try {
            // Get the execution
            WorkflowExecution execution = workflowExecutionRepository.findById(step.getExecutionId())
                    .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + step.getExecutionId()));
            
            // Check if execution is cancelled
            if (execution.getStatus() == WorkflowExecution.ExecutionStatus.CANCELLED) {
                log.info("Execution {} is cancelled, not processing Wait step {}", execution.getId(), step.getStepName());
                return;
            }
            
            // Mark step as completed
            step.setStatus(ExecutionStep.StepStatus.COMPLETED);
            step.setCompletedAt(OffsetDateTime.now());
            executionStepRepository.save(step);
            
            // Record metrics
            workflowStepCompletedCounter.increment();
            
            addExecutionHistory(step.getExecutionId(), step.getStepName(), "WAIT_COMPLETED", 
                    Map.of("completedAt", OffsetDateTime.now()));
            
            // Move to next state
            moveToNextState(execution, step, Map.of("waitCompleted", true));
            
            // Record metrics
            waitStepsProcessed.incrementAndGet();
            
            log.info("Wait step {} completed for execution: {}", step.getStepName(), step.getExecutionId());
            
        } catch (Exception e) {
            log.error("Error processing Wait step: {}", step.getId(), e);
        }
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
    
    private Integer getMaxRetries(Object stateDef) {
        if (stateDef instanceof Map) {
            Map<String, Object> stateMap = (Map<String, Object>) stateDef;
            Map<String, Object> retry = (Map<String, Object>) stateMap.get("retry");
            if (retry != null && retry.get("maxAttempts") != null) {
                return (Integer) retry.get("maxAttempts");
            }
        }
        return 3; // default
    }
    
    private Double getBackoffMultiplier(Object stateDef) {
        if (stateDef instanceof Map) {
            Map<String, Object> stateMap = (Map<String, Object>) stateDef;
            Map<String, Object> retry = (Map<String, Object>) stateMap.get("retry");
            if (retry != null && retry.get("backoffMultiplier") != null) {
                return (Double) retry.get("backoffMultiplier");
            }
        }
        return 2.0; // default
    }
    
    private Long getInitialIntervalMs(Object stateDef) {
        if (stateDef instanceof Map) {
            Map<String, Object> stateMap = (Map<String, Object>) stateDef;
            Map<String, Object> retry = (Map<String, Object>) stateMap.get("retry");
            if (retry != null && retry.get("initialIntervalMs") != null) {
                return (Long) retry.get("initialIntervalMs");
            }
        }
        return 1000L; // default
    }
    
    private Integer getTimeoutSeconds(Object stateDef) {
        if (stateDef instanceof Map) {
            Map<String, Object> stateMap = (Map<String, Object>) stateDef;
            if (stateMap.get("timeout") != null) {
                return (Integer) stateMap.get("timeout");
            }
        }
        return null; // no timeout
    }
    
    private ConditionConfig mapToConditionConfig(Map<String, Object> conditionMap) {
        log.info("🔧 Mapping condition map to ConditionConfig: {}", conditionMap);
        
        String operator = (String) conditionMap.get("operator");
        String variable = (String) conditionMap.get("variable");
        Object value = conditionMap.get("value");
        
        log.info("🔧 Extracted: operator={}, variable={}, value={}", operator, variable, value);
        
        ConditionConfig config = ConditionConfig.builder()
                .operator(operator)
                .variable(variable)
                .value(value)
                .build();
        
        log.info("🔧 Built ConditionConfig: {}", config);
        return config;
    }
}
