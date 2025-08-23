package com.freightmate.workflow.service;

import com.freightmate.workflow.dto.WorkflowExecutionRequest;
import com.freightmate.workflow.dto.WorkflowExecutionResponse;
import com.freightmate.workflow.dto.ExecutionStepResponse;
import com.freightmate.workflow.dto.ExecutionHistoryResponse;
import com.freightmate.workflow.dto.ExecutionListRequest;
import com.freightmate.workflow.entity.*;
import com.freightmate.workflow.repository.*;
import com.freightmate.workflow.task.TaskRegistry;
import com.freightmate.workflow.task.TaskResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowExecutionService {
    
    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final ExecutionStepRepository executionStepRepository;
    private final ExecutionQueueRepository executionQueueRepository;
    private final ExecutionHistoryRepository executionHistoryRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final TaskRegistry taskRegistry;
    private final ConditionEvaluatorService conditionEvaluatorService;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public WorkflowExecutionResponse startExecution(WorkflowExecutionRequest request, String idempotencyKey) {
        // Check idempotency if key provided
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            Optional<WorkflowExecution> existingExecution = findExistingExecution(idempotencyKey);
            if (existingExecution.isPresent()) {
                return mapToExecutionResponse(existingExecution.get());
            }
        }
        
        // Find workflow and version
        Workflow workflow = workflowRepository.findByName(request.getWorkflowName())
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + request.getWorkflowName()));
        
        WorkflowVersion version;
        if (request.getVersion() != null) {
            version = workflowVersionRepository.findByWorkflowIdAndVersion(workflow.getId(), request.getVersion())
                    .orElseThrow(() -> new IllegalArgumentException("Workflow version not found: " + request.getVersion()));
        } else {
            version = workflowVersionRepository.findFirstByWorkflowIdOrderByVersionDesc(workflow.getId())
                    .orElseThrow(() -> new IllegalArgumentException("No versions found for workflow: " + request.getWorkflowName()));
        }
        
        // Parse workflow definition
        Map<String, Object> definition = parseWorkflowDefinition(version.getDefinitionJsonb());
        String startAt = (String) definition.get("startAt");
        Map<String, Object> states = (Map<String, Object>) definition.get("states");
        
        if (startAt == null || !states.containsKey(startAt)) {
            throw new IllegalArgumentException("Invalid workflow definition: startAt state not found");
        }
        
        // Create execution
        String executionId = generateExecutionId();
        WorkflowExecution execution = WorkflowExecution.builder()
                .workflowVersion(version)
                .executionId(executionId)
                .status(WorkflowExecution.ExecutionStatus.RUNNING)
                .currentState(startAt)
                .inputData(request.getInput())
                .startedAt(OffsetDateTime.now())
                .build();
        
        execution = workflowExecutionRepository.save(execution);
        
        // Create first step
        ExecutionStep firstStep = ExecutionStep.builder()
                .executionId(execution.getId())
                .stepName(startAt)
                .stepType(getStepType(states.get(startAt)))
                .status(ExecutionStep.StepStatus.PENDING)
                .inputData(request.getInput())
                .retryCount(0)
                .maxRetries(3)
                .build();
        
        firstStep = executionStepRepository.save(firstStep);
        
        // Enqueue first step
        ExecutionQueue queueItem = ExecutionQueue.builder()
                .executionId(execution.getId())
                .priority(0)
                .scheduledAt(OffsetDateTime.now())
                .status(ExecutionQueue.QueueStatus.QUEUED)
                .build();
        
        executionQueueRepository.save(queueItem);
        
        // Store idempotency key if provided
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            IdempotencyKey key = IdempotencyKey.builder()
                    .keyHash(idempotencyKey)
                    .resourceType("workflow_execution")
                    .resourceId(executionId)
                    .expiresAt(OffsetDateTime.now().plusHours(24))
                    .build();
            idempotencyKeyRepository.save(key);
        }
        
        // Add to execution history
        addExecutionHistory(execution.getId(), startAt, "EXECUTION_STARTED", 
                Map.of("workflowName", request.getWorkflowName(), "version", version.getVersion()));
        
        log.info("Started workflow execution: {} for workflow: {}", executionId, request.getWorkflowName());
        
        return mapToExecutionResponse(execution);
    }
    
    @Transactional(readOnly = true)
    public WorkflowExecutionResponse getExecution(Long executionId) {
        WorkflowExecution execution = workflowExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
        
        return mapToExecutionResponse(execution);
    }
    
    @Transactional(readOnly = true)
    public ExecutionStepResponse getExecutionStep(Long executionId, Long stepId) {
        ExecutionStep step = executionStepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("Step not found: " + stepId));
        
        if (!step.getExecutionId().equals(executionId)) {
            throw new IllegalArgumentException("Step does not belong to execution: " + executionId);
        }
        
        // Get step history
        List<ExecutionHistoryResponse> history = executionHistoryRepository
                .findByExecutionIdOrderByTimestampAsc(executionId)
                .stream()
                .filter(h -> step.getStepName().equals(h.getStepName()))
                .map(this::mapToHistoryResponse)
                .collect(Collectors.toList());
        
        return mapToStepResponse(step, history);
    }
    
    @Transactional(readOnly = true)
    public List<WorkflowExecutionResponse> listExecutions(ExecutionListRequest request) {
        // Build dynamic query based on filters
        List<WorkflowExecution> executions = workflowExecutionRepository.findByFilters(
                request.getStatuses(),
                request.getWorkflowName(),
                request.getStartDate(),
                request.getEndDate(),
                request.getSortBy(),
                request.getSortOrder(),
                request.getLimit(),
                request.getOffset()
        );
        
        return executions.stream()
                .map(this::mapToExecutionResponse)
                .collect(Collectors.toList());
    }
    
    private Optional<WorkflowExecution> findExistingExecution(String idempotencyKey) {
        return idempotencyKeyRepository.findByKeyHash(idempotencyKey)
                .filter(key -> key.getExpiresAt().isAfter(OffsetDateTime.now()))
                .flatMap(key -> workflowExecutionRepository.findByExecutionId(key.getResourceId()));
    }
    
    private String generateExecutionId() {
        return "exec-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    private Map<String, Object> parseWorkflowDefinition(String definitionJsonb) {
        try {
            return objectMapper.readValue(definitionJsonb, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid workflow definition JSON", e);
        }
    }
    
    private String getStepType(Object state) {
        if (state instanceof Map) {
            Map<String, Object> stateMap = (Map<String, Object>) state;
            return (String) stateMap.getOrDefault("type", "Task");
        }
        return "Task";
    }
    
    private String convertToJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Error converting object to JSON", e);
            return "{}";
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
    
    private WorkflowExecutionResponse mapToExecutionResponse(WorkflowExecution execution) {
        return WorkflowExecutionResponse.builder()
                .id(execution.getId())
                .executionId(execution.getExecutionId())
                .workflowName(execution.getWorkflowVersion().getWorkflow().getName())
                .version(execution.getWorkflowVersion().getVersion())
                .status(execution.getStatus().name())
                .currentState(execution.getCurrentState())
                .input(execution.getInputData())
                .output(execution.getOutputData())
                .errorMessage(execution.getErrorMessage())
                .startedAt(execution.getStartedAt())
                .completedAt(execution.getCompletedAt())
                .createdAt(execution.getCreatedAt())
                .build();
    }
    
    private ExecutionStepResponse mapToStepResponse(ExecutionStep step, List<ExecutionHistoryResponse> history) {
        return ExecutionStepResponse.builder()
                .id(step.getId())
                .stepName(step.getStepName())
                .stepType(step.getStepType())
                .status(step.getStatus().name())
                .input(step.getInputData())
                .output(step.getOutputData())
                .errorMessage(step.getErrorMessage())
                .retryCount(step.getRetryCount())
                .maxRetries(step.getMaxRetries())
                .startedAt(step.getStartedAt())
                .completedAt(step.getCompletedAt())
                .createdAt(step.getCreatedAt())
                .build();
    }
    
    private ExecutionHistoryResponse mapToHistoryResponse(ExecutionHistory history) {
        return ExecutionHistoryResponse.builder()
                .id(history.getId())
                .stepName(history.getStepName())
                .eventType(history.getEventType())
                .eventData(history.getEventData())
                .timestamp(history.getTimestamp())
                .build();
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
}
