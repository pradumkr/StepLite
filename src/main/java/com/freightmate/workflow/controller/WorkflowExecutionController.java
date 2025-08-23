package com.freightmate.workflow.controller;

import com.freightmate.workflow.dto.WorkflowExecutionRequest;
import com.freightmate.workflow.dto.WorkflowExecutionResponse;
import com.freightmate.workflow.dto.ExecutionStepResponse;
import com.freightmate.workflow.service.WorkflowExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

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
}
