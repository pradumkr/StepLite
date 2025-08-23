package com.freightmate.workflow.service;

import com.freightmate.workflow.dto.WorkflowExecutionRequest;
import com.freightmate.workflow.dto.WorkflowExecutionResponse;
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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WorkflowExecutionServiceTest {

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
    private WorkflowExecutionService workflowExecutionService;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private WorkflowVersionRepository workflowVersionRepository;

    private Workflow testWorkflow;
    private WorkflowVersion testVersion;

    @BeforeEach
    void setUp() {
        // Create test workflow
        testWorkflow = Workflow.builder()
                .name("simple-test-workflow")
                .description("Simple test workflow for execution engine testing")
                .build();
        testWorkflow = workflowRepository.save(testWorkflow);

        // Create test version with workflow definition
        String workflowDefinition = "{" +
            "\"name\": \"simple-test-workflow\"," +
            "\"version\": \"1.0.0\"," +
            "\"description\": \"Simple test workflow for execution engine testing\"," +
            "\"startAt\": \"start\"," +
            "\"states\": {" +
                "\"start\": {" +
                    "\"type\": \"Task\"," +
                    "\"next\": \"process\"," +
                    "\"parameters\": {" +
                        "\"taskType\": \"mock\"," +
                        "\"timeout\": 1000" +
                    "}" +
                "}," +
                "\"process\": {" +
                    "\"type\": \"Task\"," +
                    "\"next\": \"complete\"," +
                    "\"parameters\": {" +
                        "\"taskType\": \"mock\"," +
                        "\"simulateError\": false" +
                    "}" +
                "}," +
                "\"complete\": {" +
                    "\"type\": \"Success\"" +
                "}" +
            "}" +
        "}";

        testVersion = WorkflowVersion.builder()
                .workflow(testWorkflow)
                .version("1.0.0")
                .definitionJsonb(workflowDefinition)
                .isActive(true)
                .build();
        testVersion = workflowVersionRepository.save(testVersion);
    }

    @Test
    void shouldStartWorkflowExecution() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("orderId", "order-123");
        input.put("customerId", "customer-456");
        input.put("amount", 100.50);

        WorkflowExecutionRequest request = WorkflowExecutionRequest.builder()
                .workflowName("simple-test-workflow")
                .version("1.0.0")
                .input(input)
                .build();

        // When
        WorkflowExecutionResponse response = workflowExecutionService.startExecution(request, null);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getExecutionId()).isNotNull();
        assertThat(response.getWorkflowName()).isEqualTo("simple-test-workflow");
        assertThat(response.getVersion()).isEqualTo("1.0.0");
        assertThat(response.getStatus()).isEqualTo("RUNNING");
        assertThat(response.getCurrentState()).isEqualTo("start");
        assertThat(response.getInput()).containsEntry("orderId", "order-123");
        assertThat(response.getInput()).containsEntry("customerId", "customer-456");
        assertThat(response.getInput()).containsEntry("amount", 100.50);
        assertThat(response.getStartedAt()).isNotNull();
        assertThat(response.getCompletedAt()).isNull();
    }

    @Test
    void shouldStartWorkflowExecutionWithLatestVersion() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("testData", "latest-version-test");

        WorkflowExecutionRequest request = WorkflowExecutionRequest.builder()
                .workflowName("simple-test-workflow")
                .input(input)
                .build();

        // When
        WorkflowExecutionResponse response = workflowExecutionService.startExecution(request, null);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getVersion()).isEqualTo("1.0.0"); // Should use latest version
        assertThat(response.getStatus()).isEqualTo("RUNNING");
        assertThat(response.getCurrentState()).isEqualTo("start");
    }

    @Test
    void shouldHandleIdempotencyKey() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("idempotencyTest", true);

        WorkflowExecutionRequest request = WorkflowExecutionRequest.builder()
                .workflowName("simple-test-workflow")
                .input(input)
                .build();

        String idempotencyKey = "test-key-123";

        // When - First execution
        WorkflowExecutionResponse firstResponse = workflowExecutionService.startExecution(request, idempotencyKey);

        // Then - Should create new execution
        assertThat(firstResponse).isNotNull();
        assertThat(firstResponse.getExecutionId()).isNotNull();

        // When - Second execution with same key
        WorkflowExecutionResponse secondResponse = workflowExecutionService.startExecution(request, idempotencyKey);

        // Then - Should return existing execution
        assertThat(secondResponse).isNotNull();
        assertThat(secondResponse.getExecutionId()).isEqualTo(firstResponse.getExecutionId());
        assertThat(secondResponse.getId()).isEqualTo(firstResponse.getId());
    }

    @Test
    void shouldThrowExceptionForNonExistentWorkflow() {
        // Given
        WorkflowExecutionRequest request = WorkflowExecutionRequest.builder()
                .workflowName("non-existent-workflow")
                .input(new HashMap<>())
                .build();

        // When & Then
        assertThatThrownBy(() -> workflowExecutionService.startExecution(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Workflow not found: non-existent-workflow");
    }

    @Test
    void shouldThrowExceptionForNonExistentVersion() {
        // Given
        WorkflowExecutionRequest request = WorkflowExecutionRequest.builder()
                .workflowName("simple-test-workflow")
                .version("2.0.0")
                .input(new HashMap<>())
                .build();

        // When & Then
        assertThatThrownBy(() -> workflowExecutionService.startExecution(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Workflow version not found: 2.0.0");
    }
}
