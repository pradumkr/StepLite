package com.freightmate.workflow.controller;

import com.freightmate.workflow.dto.WorkflowDefinitionDTO;
import com.freightmate.workflow.dto.WorkflowRegistrationResponse;
import com.freightmate.workflow.dto.WorkflowResponseDTO;
import com.freightmate.workflow.service.WorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workflows")
@RequiredArgsConstructor
@Slf4j
public class WorkflowController {
    
    private final WorkflowService workflowService;
    
    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    public ResponseEntity<WorkflowRegistrationResponse> registerWorkflow(
            @Valid @RequestBody WorkflowDefinitionDTO definitionDTO,
            HttpServletRequest request) {
        
        log.info("Received workflow registration request for: {} version: {}", 
                definitionDTO.getName(), definitionDTO.getVersion());
        
        String contentType = request.getContentType();
        Long workflowVersionId = workflowService.registerWorkflow(definitionDTO, contentType);
        
        WorkflowRegistrationResponse response = WorkflowRegistrationResponse.builder()
                .workflowVersionId(workflowVersionId)
                .message("Workflow registered successfully")
                .build();
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping
    public ResponseEntity<List<WorkflowResponseDTO>> getAllWorkflows() {
        log.info("Fetching all workflows");
        List<WorkflowResponseDTO> workflows = workflowService.getAllWorkflows();
        return ResponseEntity.ok(workflows);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<WorkflowResponseDTO> getWorkflowById(@PathVariable Long id) {
        log.info("Fetching workflow by ID: {}", id);
        WorkflowResponseDTO workflow = workflowService.getWorkflowById(id);
        return ResponseEntity.ok(workflow);
    }
}
