package com.freightmate.workflow.controller;

import com.freightmate.workflow.dto.WorkflowExecutionRequest;
import com.freightmate.workflow.dto.WorkflowExecutionResponse;
import com.freightmate.workflow.dto.ExecutionStepResponse;
import com.freightmate.workflow.dto.ExecutionListRequest;
import com.freightmate.workflow.service.WorkflowExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/workflow-executions")
@RequiredArgsConstructor
@Slf4j
public class WorkflowExecutionController {
    
    private final WorkflowExecutionService workflowExecutionService;
    
    @PostMapping
    public ResponseEntity<WorkflowExecutionResponse> startExecution(
            @Valid @RequestBody WorkflowExecutionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        
        log.info("Starting workflow execution for workflow: {} with idempotency key: {}", 
                request.getWorkflowName(), idempotencyKey);
        
        WorkflowExecutionResponse response = workflowExecutionService.startExecution(request, idempotencyKey);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    public ResponseEntity<List<WorkflowExecutionResponse>> listExecutions(
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) String workflowName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder) {
        
        log.info("Listing workflow executions with filters: workflowName={}, statuses={}, limit={}, offset={}", 
                workflowName, statuses, limit, offset);
        
        ExecutionListRequest request = ExecutionListRequest.builder()
                .statuses(statuses)
                .workflowName(workflowName)
                .startDate(startDate)
                .endDate(endDate)
                .limit(limit)
                .offset(offset)
                .sortBy(sortBy)
                .sortOrder(sortOrder)
                .build();
        
        List<WorkflowExecutionResponse> executions = workflowExecutionService.listExecutions(request);
        return ResponseEntity.ok(executions);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<WorkflowExecutionResponse> getExecution(@PathVariable("id") Long executionId) {
        log.info("Getting workflow execution: {}", executionId);
        
        WorkflowExecutionResponse response = workflowExecutionService.getExecution(executionId);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{id}/steps/{stepId}")
    public ResponseEntity<ExecutionStepResponse> getExecutionStep(
            @PathVariable("id") Long executionId,
            @PathVariable("stepId") Long stepId) {
        
        log.info("Getting execution step: {} for execution: {}", stepId, executionId);
        
        ExecutionStepResponse response = workflowExecutionService.getExecutionStep(executionId, stepId);
        
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{id}/cancel")
    public ResponseEntity<WorkflowExecutionResponse> cancelExecution(@PathVariable("id") Long executionId) {
        log.info("Cancelling workflow execution: {}", executionId);
        
        WorkflowExecutionResponse response = workflowExecutionService.cancelExecution(executionId);
        
        return ResponseEntity.ok(response);
    }
}
