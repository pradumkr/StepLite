package com.freightmate.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.freightmate.workflow.dto.WorkflowDefinitionDTO;
import com.freightmate.workflow.dto.WorkflowResponseDTO;
import com.freightmate.workflow.entity.Workflow;
import com.freightmate.workflow.entity.WorkflowVersion;
import com.freightmate.workflow.repository.WorkflowRepository;
import com.freightmate.workflow.repository.WorkflowVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {
    
    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    
    @Transactional
    public Long registerWorkflow(WorkflowDefinitionDTO definitionDTO, String contentType) {
        log.info("Registering workflow: {} version: {}", definitionDTO.getName(), definitionDTO.getVersion());
        
        // Convert YAML to JSON if needed
        String definitionJson = convertToJson(definitionDTO, contentType);
        
        // Find or create workflow
        Workflow workflow = workflowRepository.findByName(definitionDTO.getName())
                .orElse(Workflow.builder()
                        .name(definitionDTO.getName())
                        .description(definitionDTO.getDescription())
                        .build());
        
        if (workflow.getId() == null) {
            workflow = workflowRepository.save(workflow);
        }
        
        // Check version uniqueness
        if (workflowVersionRepository.existsByWorkflowIdAndVersion(workflow.getId(), definitionDTO.getVersion())) {
            throw new IllegalArgumentException("Workflow version already exists: " + definitionDTO.getVersion());
        }
        
        // Create workflow version
        WorkflowVersion version = WorkflowVersion.builder()
                .workflow(workflow)
                .version(definitionDTO.getVersion())
                .definitionJsonb(definitionJson)
                .isActive(false)
                .build();
        
        WorkflowVersion savedVersion = workflowVersionRepository.save(version);
        log.info("Workflow version created with ID: {}", savedVersion.getId());
        
        return savedVersion.getId();
    }
    
    @Transactional(readOnly = true)
    public List<WorkflowResponseDTO> getAllWorkflows() {
        log.info("Fetching all workflows");
        List<Workflow> workflows = workflowRepository.findAllWithVersions();
        
        return workflows.stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public WorkflowResponseDTO getWorkflowById(Long id) {
        log.info("Fetching workflow by ID: {}", id);
        Workflow workflow = workflowRepository.findByIdWithVersions(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + id));
        
        return mapToResponseDTO(workflow);
    }
    
    private String convertToJson(WorkflowDefinitionDTO definitionDTO, String contentType) {
        try {
            if (contentType != null && contentType.contains("yaml")) {
                // Convert YAML to JSON
                String yamlString = yamlMapper.writeValueAsString(definitionDTO);
                Object yamlObject = yamlMapper.readValue(yamlString, Object.class);
                return jsonMapper.writeValueAsString(yamlObject);
            } else {
                // Already JSON
                return jsonMapper.writeValueAsString(definitionDTO);
            }
        } catch (JsonProcessingException e) {
            log.error("Error converting workflow definition", e);
            throw new RuntimeException("Invalid workflow definition format", e);
        }
    }
    
    private WorkflowResponseDTO mapToResponseDTO(Workflow workflow) {
        List<WorkflowResponseDTO.WorkflowVersionDTO> versionDTOs = workflow.getVersions().stream()
                .map(version -> WorkflowResponseDTO.WorkflowVersionDTO.builder()
                        .id(version.getId())
                        .version(version.getVersion())
                        .definitionJsonb(version.getDefinitionJsonb())
                        .isActive(version.getIsActive())
                        .createdAt(version.getCreatedAt())
                        .updatedAt(version.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
        
        return WorkflowResponseDTO.builder()
                .id(workflow.getId())
                .name(workflow.getName())
                .description(workflow.getDescription())
                .createdAt(workflow.getCreatedAt())
                .updatedAt(workflow.getUpdatedAt())
                .versions(versionDTOs)
                .build();
    }
}
