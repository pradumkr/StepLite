package com.freightmate.workflow.service;

import com.freightmate.workflow.dto.WorkflowDefinitionDTO;
import com.freightmate.workflow.dto.WorkflowResponseDTO;
import com.freightmate.workflow.entity.Workflow;
import com.freightmate.workflow.entity.WorkflowVersion;
import com.freightmate.workflow.repository.WorkflowRepository;
import com.freightmate.workflow.repository.WorkflowVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WorkflowServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        
        // Redis configuration
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
        registry.add("spring.redis.timeout", () -> "2000ms");
    }

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private WorkflowVersionRepository workflowVersionRepository;

    private WorkflowDefinitionDTO validDefinition;
    private WorkflowDefinitionDTO yamlDefinition;

    @BeforeEach
    void setUp() {
        // Create valid workflow definition
        Map<String, Object> states = new HashMap<>();
        states.put("start", Map.of("type", "Task", "next", "end"));
        states.put("end", Map.of("type", "Success"));

        validDefinition = WorkflowDefinitionDTO.builder()
                .name("test-workflow")
                .version("1.0.0")
                .description("Test workflow")
                .startAt("start")
                .states(states)
                .build();

        // Create YAML-style workflow definition
        Map<String, Object> yamlStates = new HashMap<>();
        yamlStates.put("initialize", Map.of("type", "Initialize", "next", "process"));
        yamlStates.put("process", Map.of("type", "Process", "next", "finalize"));
        yamlStates.put("finalize", Map.of("type", "Finalize"));

        yamlDefinition = WorkflowDefinitionDTO.builder()
                .name("yaml-workflow")
                .version("2.0.0")
                .description("YAML workflow test")
                .startAt("initialize")
                .states(yamlStates)
                .build();
    }

    @Test
    void shouldRegisterNewWorkflowSuccessfully() {
        // When
        Long workflowVersionId = workflowService.registerWorkflow(validDefinition, "application/json");

        // Then
        assertThat(workflowVersionId).isNotNull();
        assertThat(workflowVersionId).isPositive();

        // Verify workflow was created
        Workflow savedWorkflow = workflowRepository.findByName("test-workflow").orElse(null);
        assertThat(savedWorkflow).isNotNull();
        assertThat(savedWorkflow.getName()).isEqualTo("test-workflow");
        assertThat(savedWorkflow.getDescription()).isEqualTo("Test workflow");

        // Verify version was created with JSONB data
        WorkflowVersion savedVersion = workflowVersionRepository.findByWorkflowIdAndVersion(
                savedWorkflow.getId(), "1.0.0").orElse(null);
        assertThat(savedVersion).isNotNull();
        assertThat(savedVersion.getDefinitionJsonb()).contains("test-workflow");
        assertThat(savedVersion.getDefinitionJsonb()).contains("1.0.0");
        assertThat(savedVersion.getDefinitionJsonb()).contains("start");
        assertThat(savedVersion.getDefinitionJsonb()).contains("end");
    }

    @Test
    void shouldRegisterNewVersionForExistingWorkflow() {
        // Given - Register first version
        workflowService.registerWorkflow(validDefinition, "application/json");

        // When - Register second version
        WorkflowDefinitionDTO secondVersion = WorkflowDefinitionDTO.builder()
                .name("test-workflow")
                .version("1.1.0")
                .description("Updated test workflow")
                .startAt("start")
                .states(validDefinition.getStates())
                .build();

        Long workflowVersionId = workflowService.registerWorkflow(secondVersion, "application/json");

        // Then
        assertThat(workflowVersionId).isNotNull();
        assertThat(workflowVersionId).isPositive();

        // Verify only one workflow exists
        List<Workflow> workflows = workflowRepository.findAll();
        assertThat(workflows).hasSize(1);

        // Verify two versions exist
        Workflow workflow = workflows.get(0);
        List<WorkflowVersion> versions = workflowVersionRepository.findByWorkflowId(workflow.getId());
        assertThat(versions).hasSize(2);
        assertThat(versions).anyMatch(v -> v.getVersion().equals("1.0.0"));
        assertThat(versions).anyMatch(v -> v.getVersion().equals("1.1.0"));
    }

    @Test
    void shouldThrowExceptionWhenVersionAlreadyExists() {
        // Given - Register first version
        workflowService.registerWorkflow(validDefinition, "application/json");

        // When & Then - Try to register same version again
        assertThatThrownBy(() -> workflowService.registerWorkflow(validDefinition, "application/json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Workflow version already exists: 1.0.0");
    }

    @Test
    void shouldConvertYamlToJsonWhenContentTypeIsYaml() {
        // When
        Long workflowVersionId = workflowService.registerWorkflow(yamlDefinition, "application/x-yaml");

        // Then
        assertThat(workflowVersionId).isNotNull();

        // Verify workflow was created
        Workflow savedWorkflow = workflowRepository.findByName("yaml-workflow").orElse(null);
        assertThat(savedWorkflow).isNotNull();

        // Verify version was created with JSONB data
        WorkflowVersion savedVersion = workflowVersionRepository.findByWorkflowIdAndVersion(
                savedWorkflow.getId(), "2.0.0").orElse(null);
        assertThat(savedVersion).isNotNull();
        assertThat(savedVersion.getDefinitionJsonb()).contains("yaml-workflow");
        assertThat(savedVersion.getDefinitionJsonb()).contains("2.0.0");
        assertThat(savedVersion.getDefinitionJsonb()).contains("initialize");
        assertThat(savedVersion.getDefinitionJsonb()).contains("process");
        assertThat(savedVersion.getDefinitionJsonb()).contains("finalize");
    }

    @Test
    void shouldGetAllWorkflowsSuccessfully() {
        // Given - Register workflows
        workflowService.registerWorkflow(validDefinition, "application/json");
        workflowService.registerWorkflow(yamlDefinition, "application/json");

        // When
        List<WorkflowResponseDTO> result = workflowService.getAllWorkflows();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).anyMatch(w -> w.getName().equals("test-workflow"));
        assertThat(result).anyMatch(w -> w.getName().equals("yaml-workflow"));
        
        // Verify versions are included
        WorkflowResponseDTO testWorkflow = result.stream()
                .filter(w -> w.getName().equals("test-workflow"))
                .findFirst().orElse(null);
        assertThat(testWorkflow).isNotNull();
        assertThat(testWorkflow.getVersions()).hasSize(1);
        assertThat(testWorkflow.getVersions().get(0).getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void shouldGetWorkflowByIdSuccessfully() {
        // Given - Register workflow
        Long workflowVersionId = workflowService.registerWorkflow(validDefinition, "application/json");
        Workflow savedWorkflow = workflowRepository.findByName("test-workflow").orElse(null);
        assertThat(savedWorkflow).isNotNull();

        // When
        WorkflowResponseDTO result = workflowService.getWorkflowById(savedWorkflow.getId());

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("test-workflow");
        assertThat(result.getVersions()).hasSize(1);
        assertThat(result.getVersions().get(0).getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void shouldThrowExceptionWhenWorkflowNotFound() {
        // When & Then
        assertThatThrownBy(() -> workflowService.getWorkflowById(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Workflow not found: 999");
    }

    @Test
    void shouldValidateRequiredFields() {
        // Test missing name
        WorkflowDefinitionDTO invalidDefinition = WorkflowDefinitionDTO.builder()
                .version("1.0.0")
                .startAt("start")
                .states(new HashMap<>())
                .build();

        assertThatThrownBy(() -> workflowService.registerWorkflow(invalidDefinition, "application/json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldHandleComplexJsonbData() {
        // Given - Create complex workflow definition
        Map<String, Object> complexStates = new HashMap<>();
        Map<String, Object> taskState = new HashMap<>();
        taskState.put("type", "Task");
        taskState.put("next", "choice");
        taskState.put("parameters", Map.of(
            "timeout", 300,
            "retries", 3,
            "metadata", Map.of("priority", "high", "tags", Arrays.asList("urgent", "critical"))
        ));
        
        Map<String, Object> choiceState = new HashMap<>();
        choiceState.put("type", "Choice");
        choiceState.put("choices", Arrays.asList(
            Map.of("condition", "success", "next", "success"),
            Map.of("condition", "failure", "next", "error")
        ));
        
        complexStates.put("start", taskState);
        complexStates.put("choice", choiceState);
        complexStates.put("success", Map.of("type", "Success"));
        complexStates.put("error", Map.of("type", "Fail", "error", "Workflow failed"));

        WorkflowDefinitionDTO complexDefinition = WorkflowDefinitionDTO.builder()
                .name("complex-workflow")
                .version("3.0.0")
                .description("Complex workflow with nested JSONB data")
                .startAt("start")
                .states(complexStates)
                .build();

        // When
        Long workflowVersionId = workflowService.registerWorkflow(complexDefinition, "application/json");

        // Then
        assertThat(workflowVersionId).isNotNull();

        // Verify complex JSONB data was stored correctly
        Workflow savedWorkflow = workflowRepository.findByName("complex-workflow").orElse(null);
        assertThat(savedWorkflow).isNotNull();

        WorkflowVersion savedVersion = workflowVersionRepository.findByWorkflowIdAndVersion(
                savedWorkflow.getId(), "3.0.0").orElse(null);
        assertThat(savedVersion).isNotNull();
        
        String jsonbData = savedVersion.getDefinitionJsonb();
        assertThat(jsonbData).contains("complex-workflow");
        assertThat(jsonbData).contains("3.0.0");
        assertThat(jsonbData).contains("Task");
        assertThat(jsonbData).contains("Choice");
        assertThat(jsonbData).contains("timeout");
        assertThat(jsonbData).contains("300");
        assertThat(jsonbData).contains("priority");
        assertThat(jsonbData).contains("high");
        assertThat(jsonbData).contains("urgent");
        assertThat(jsonbData).contains("critical");
    }
}
