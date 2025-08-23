package com.freightmate.workflow.integration;

import com.freightmate.workflow.dto.WorkflowDefinitionDTO;
import com.freightmate.workflow.dto.StateDefinition;
import com.freightmate.workflow.dto.ChoiceConfig;
import com.freightmate.workflow.dto.ConditionConfig;
import com.freightmate.workflow.entity.Workflow;
import com.freightmate.workflow.entity.WorkflowVersion;
import com.freightmate.workflow.repository.WorkflowRepository;
import com.freightmate.workflow.repository.WorkflowVersionRepository;
import com.freightmate.workflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class JsonbIntegrationTest {

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
        registry.add("spring.flyway.enabled", () -> false);
        
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

    @BeforeEach
    void setUp() {
        // Clean up any existing data
        workflowVersionRepository.deleteAll();
        workflowRepository.deleteAll();
    }

    @Test
    void shouldStoreAndRetrieveComplexJsonbData() {
        // Given - Create a complex workflow definition
        Map<String, StateDefinition> states = new HashMap<>();
        
        // State 1: Initialize with complex parameters
        StateDefinition initializeState = StateDefinition.builder()
                .type("Initialize")
                .next("process")
                .parameters(Map.of(
                    "timeout", 300,
                    "retries", 3,
                    "metadata", Map.of(
                        "priority", "high",
                        "tags", List.of("urgent", "critical"),
                        "config", Map.of(
                            "maxConcurrency", 5,
                            "enableLogging", true,
                            "nested", Map.of(
                                "level1", Map.of(
                                    "level2", Map.of(
                                        "level3", "deep_value"
                                    )
                                )
                            )
                        )
                    )
                ))
                .build();
        states.put("initialize", initializeState);

        // State 2: Process with input/output paths
        StateDefinition processState = StateDefinition.builder()
                .type("Task")
                .next("choice")
                .parameters(Map.of(
                    "inputPath", "$.data",
                    "outputPath", "$.result",
                    "retry", List.of(
                        Map.of("errorEquals", "States.Timeout", "maxAttempts", 3),
                        Map.of("errorEquals", "States.TaskFailed", "maxAttempts", 2)
                    )
                ))
                .build();
        states.put("process", processState);

        // State 3: Choice with complex conditions
        List<ChoiceConfig> choices = List.of(
            ChoiceConfig.builder()
                .next("success")
                .condition(ConditionConfig.builder()
                    .operator("stringEquals")
                    .variable("$.result.status")
                    .value("success")
                    .build())
                .build(),
            ChoiceConfig.builder()
                .next("error")
                .condition(ConditionConfig.builder()
                    .operator("stringEquals")
                    .variable("$.result.error")
                    .value("failure")
                    .build())
                .build(),
            ChoiceConfig.builder()
                .next("process")
                .condition(ConditionConfig.builder()
                    .operator("numericGreaterThan")
                    .variable("$.result.retryCount")
                    .value(0)
                    .build())
                .build()
        );
        
        StateDefinition choiceState = StateDefinition.builder()
                .type("Choice")
                .choices(choices)
                .defaultChoice("success")
                .build();
        states.put("choice", choiceState);

        // State 4: Success
        StateDefinition successState = StateDefinition.builder()
                .type("Success")
                .build();
        states.put("success", successState);

        // State 5: Error with cause
        StateDefinition errorState = StateDefinition.builder()
                .type("Fail")
                .parameters(Map.of(
                    "error", "Workflow failed",
                    "cause", "Processing error occurred",
                    "metadata", Map.of(
                        "errorCode", "WF_001",
                        "severity", "high",
                        "timestamp", System.currentTimeMillis()
                    )
                ))
                .build();
        states.put("error", errorState);

        WorkflowDefinitionDTO complexDefinition = WorkflowDefinitionDTO.builder()
                .name("complex-jsonb-workflow")
                .version("1.0.0")
                .description("Complex workflow testing JSONB capabilities")
                .startAt("initialize")
                .states(states)
                .build();

        // When - Register the workflow
        Long workflowVersionId = workflowService.registerWorkflow(complexDefinition, "application/json");

        // Then - Verify the workflow was created
        assertThat(workflowVersionId).isNotNull();
        assertThat(workflowVersionId).isPositive();

        // Verify workflow entity
        Workflow savedWorkflow = workflowRepository.findByName("complex-jsonb-workflow").orElse(null);
        assertThat(savedWorkflow).isNotNull();
        assertThat(savedWorkflow.getName()).isEqualTo("complex-jsonb-workflow");
        assertThat(savedWorkflow.getDescription()).isEqualTo("Complex workflow testing JSONB capabilities");

        // Verify version with JSONB data
        WorkflowVersion savedVersion = workflowVersionRepository.findByWorkflowIdAndVersion(
                savedWorkflow.getId(), "1.0.0").orElse(null);
        assertThat(savedVersion).isNotNull();
        assertThat(savedVersion.getDefinitionJsonb()).isNotNull();

        // Verify complex JSONB content is preserved
        String jsonbData = savedVersion.getDefinitionJsonb();
        
        // Basic workflow info
        assertThat(jsonbData).contains("complex-jsonb-workflow");
        assertThat(jsonbData).contains("1.0.0");
        assertThat(jsonbData).contains("Complex workflow testing JSONB capabilities");
        
        // States
        assertThat(jsonbData).contains("initialize");
        assertThat(jsonbData).contains("process");
        assertThat(jsonbData).contains("choice");
        assertThat(jsonbData).contains("success");
        assertThat(jsonbData).contains("error");
        
        // State types
        assertThat(jsonbData).contains("Initialize");
        assertThat(jsonbData).contains("Task");
        assertThat(jsonbData).contains("Choice");
        assertThat(jsonbData).contains("Success");
        assertThat(jsonbData).contains("Fail");
        
        // Complex parameters
        assertThat(jsonbData).contains("timeout");
        assertThat(jsonbData).contains("300");
        assertThat(jsonbData).contains("retries");
        assertThat(jsonbData).contains("3");
        assertThat(jsonbData).contains("priority");
        assertThat(jsonbData).contains("high");
        assertThat(jsonbData).contains("urgent");
        assertThat(jsonbData).contains("critical");
        assertThat(jsonbData).contains("maxConcurrency");
        assertThat(jsonbData).contains("5");
        assertThat(jsonbData).contains("enableLogging");
        assertThat(jsonbData).contains("true");
        assertThat(jsonbData).contains("level1");
        assertThat(jsonbData).contains("level2");
        assertThat(jsonbData).contains("level3");
        assertThat(jsonbData).contains("deep_value");
        
        // Input/output paths
        assertThat(jsonbData).contains("inputPath");
        assertThat(jsonbData).contains("$.data");
        assertThat(jsonbData).contains("outputPath");
        assertThat(jsonbData).contains("$.result");
        
        // Retry configuration
        assertThat(jsonbData).contains("retry");
        assertThat(jsonbData).contains("States.Timeout");
        assertThat(jsonbData).contains("maxAttempts");
        assertThat(jsonbData).contains("States.TaskFailed");
        
        // Choice conditions
        assertThat(jsonbData).contains("success");
        assertThat(jsonbData).contains("failure");
        assertThat(jsonbData).contains("retry");
        assertThat(jsonbData).contains("$.result.status");
        assertThat(jsonbData).contains("$.result.error");
        assertThat(jsonbData).contains("$.result.retryCount");
        
        // Error details
        assertThat(jsonbData).contains("Workflow failed");
        assertThat(jsonbData).contains("Processing error occurred");
        assertThat(jsonbData).contains("WF_001");
        assertThat(jsonbData).contains("high");
    }

    @Test
    void shouldHandleJsonbQueriesAndUpdates() {
        // Given - Create multiple workflows with different JSONB content
        createWorkflowWithJsonb("workflow-1", "1.0.0", Map.of("type", "simple", "priority", "low"));
        createWorkflowWithJsonb("workflow-2", "1.0.0", Map.of("type", "complex", "priority", "high"));
        createWorkflowWithJsonb("workflow-3", "1.0.0", Map.of("type", "simple", "priority", "medium"));

        // When - Query workflows by JSONB content
        List<WorkflowVersion> highPriorityVersions = workflowVersionRepository.findAll().stream()
                .filter(v -> v.getDefinitionJsonb().contains("\"priority\":\"high\""))
                .toList();

        List<WorkflowVersion> simpleTypeVersions = workflowVersionRepository.findAll().stream()
                .filter(v -> v.getDefinitionJsonb().contains("\"type\":\"simple\""))
                .toList();

        // Then - Verify JSONB queries work
        assertThat(highPriorityVersions).hasSize(1);
        assertThat(highPriorityVersions.get(0).getWorkflow().getName()).isEqualTo("workflow-2");

        assertThat(simpleTypeVersions).hasSize(2);
        assertThat(simpleTypeVersions).anyMatch(v -> v.getWorkflow().getName().equals("workflow-1"));
        assertThat(simpleTypeVersions).anyMatch(v -> v.getWorkflow().getName().equals("workflow-3"));

        // Test updating JSONB content
        WorkflowVersion versionToUpdate = highPriorityVersions.get(0);
        String updatedJsonb = versionToUpdate.getDefinitionJsonb()
                .replace("\"priority\":\"high\"", "\"priority\":\"critical\"");
        versionToUpdate.setDefinitionJsonb(updatedJsonb);
        workflowVersionRepository.save(versionToUpdate);

        // Verify update was persisted
        Optional<WorkflowVersion> updatedVersion = workflowVersionRepository.findById(versionToUpdate.getId());
        assertThat(updatedVersion).isPresent();
        assertThat(updatedVersion.get().getDefinitionJsonb()).contains("\"priority\":\"critical\"");
        assertThat(updatedVersion.get().getDefinitionJsonb()).doesNotContain("\"priority\":\"high\"");
    }

    @Test
    void shouldHandleLargeJsonbData() {
        // Given - Create a large JSONB payload
        StringBuilder largeJsonb = new StringBuilder();
        largeJsonb.append("{");
        largeJsonb.append("\"name\":\"large-jsonb-workflow\",");
        largeJsonb.append("\"version\":\"1.0.0\",");
        largeJsonb.append("\"description\":\"Workflow with large JSONB payload\",");
        largeJsonb.append("\"metadata\":{");
        
        // Add many nested objects and arrays
        for (int i = 0; i < 100; i++) {
            largeJsonb.append("\"object_").append(i).append("\":{");
            largeJsonb.append("\"id\":").append(i).append(",");
            largeJsonb.append("\"name\":\"item_").append(i).append("\",");
            largeJsonb.append("\"tags\":[");
            for (int j = 0; j < 10; j++) {
                largeJsonb.append("\"tag_").append(i).append("_").append(j).append("\"");
                if (j < 9) largeJsonb.append(",");
            }
            largeJsonb.append("],");
            largeJsonb.append("\"config\":{");
            largeJsonb.append("\"enabled\":").append(i % 2 == 0).append(",");
            largeJsonb.append("\"timeout\":").append(1000 + i).append(",");
            largeJsonb.append("\"retries\":").append(i % 5);
            largeJsonb.append("}");
            largeJsonb.append("}");
            if (i < 99) largeJsonb.append(",");
        }
        
        largeJsonb.append("}");
        largeJsonb.append("}");

        // When - Store the large JSONB
        Workflow workflow = workflowRepository.save(Workflow.builder()
                .name("large-workflow")
                .description("Large JSONB test")
                .build());

        WorkflowVersion largeVersion = WorkflowVersion.builder()
                .workflow(workflow)
                .version("1.0.0")
                .definitionJsonb(largeJsonb.toString())
                .isActive(false)
                .build();

        WorkflowVersion savedLargeVersion = workflowVersionRepository.save(largeVersion);

        // Then - Verify large JSONB was stored and can be retrieved
        assertThat(savedLargeVersion.getId()).isNotNull();
        assertThat(savedLargeVersion.getDefinitionJsonb()).isNotNull();
        assertThat(savedLargeVersion.getDefinitionJsonb()).contains("large-jsonb-workflow");
        assertThat(savedLargeVersion.getDefinitionJsonb()).contains("object_0");
        assertThat(savedLargeVersion.getDefinitionJsonb()).contains("object_99");
        assertThat(savedLargeVersion.getDefinitionJsonb()).contains("tag_0_0");
        assertThat(savedLargeVersion.getDefinitionJsonb()).contains("tag_99_9");

        // Verify we can retrieve it back
        Optional<WorkflowVersion> retrievedVersion = workflowVersionRepository.findById(savedLargeVersion.getId());
        assertThat(retrievedVersion).isPresent();
        assertThat(retrievedVersion.get().getDefinitionJsonb()).isEqualTo(largeJsonb.toString());
    }

    private void createWorkflowWithJsonb(String name, String version, Map<String, Object> metadata) {
        Workflow workflow = workflowRepository.save(Workflow.builder()
                .name(name)
                .description("Test workflow: " + name)
                .build());

        Map<String, Object> jsonbData = new HashMap<>();
        jsonbData.put("name", name);
        jsonbData.put("version", version);
        jsonbData.put("metadata", metadata);

        WorkflowVersion workflowVersion = WorkflowVersion.builder()
                .workflow(workflow)
                .version(version)
                .definitionJsonb(convertToJsonString(jsonbData))
                .isActive(false)
                .build();

        workflowVersionRepository.save(workflowVersion);
    }

    private String convertToJsonString(Map<String, Object> data) {
        // Simple JSON conversion for testing
        StringBuilder json = new StringBuilder();
        json.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":");
            if (entry.getValue() instanceof String) {
                json.append("\"").append(entry.getValue()).append("\"");
            } else {
                json.append(entry.getValue());
            }
            first = false;
        }
        json.append("}");
        return json.toString();
    }
}
