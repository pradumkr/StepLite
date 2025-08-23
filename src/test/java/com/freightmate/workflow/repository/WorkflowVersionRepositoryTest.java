package com.freightmate.workflow.repository;

import com.freightmate.workflow.entity.Workflow;
import com.freightmate.workflow.entity.WorkflowVersion;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WorkflowVersionRepositoryTest {

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
    private WorkflowVersionRepository workflowVersionRepository;

    @Autowired
    private com.freightmate.workflow.repository.WorkflowRepository workflowRepository;

    private Workflow testWorkflow;
    private WorkflowVersion testVersion;

    @BeforeEach
    void setUp() {
        // Create test workflow
        testWorkflow = Workflow.builder()
                .name("test-workflow")
                .description("Test workflow for unit testing")
                .build();
        testWorkflow = workflowRepository.save(testWorkflow);

        // Create test version with JSONB data
        testVersion = WorkflowVersion.builder()
                .workflow(testWorkflow)
                .version("1.0.0")
                .definitionJsonb("{\"name\":\"test\",\"version\":\"1.0.0\",\"states\":{\"start\":{\"type\":\"Task\",\"next\":\"end\"},\"end\":{\"type\":\"Success\"}}}")
                .isActive(false)
                .build();
        testVersion = workflowVersionRepository.save(testVersion);
    }

    @Test
    void shouldFindByWorkflowId() {
        List<WorkflowVersion> versions = workflowVersionRepository.findByWorkflowId(testWorkflow.getId());
        
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).getVersion()).isEqualTo("1.0.0");
        assertThat(versions.get(0).getDefinitionJsonb()).contains("test");
        assertThat(versions.get(0).getDefinitionJsonb()).contains("1.0.0");
        assertThat(versions.get(0).getDefinitionJsonb()).contains("start");
        assertThat(versions.get(0).getDefinitionJsonb()).contains("end");
    }

    @Test
    void shouldFindByWorkflowIdAndVersion() {
        Optional<WorkflowVersion> version = workflowVersionRepository.findByWorkflowIdAndVersion(
                testWorkflow.getId(), "1.0.0");
        
        assertThat(version).isPresent();
        assertThat(version.get().getVersion()).isEqualTo("1.0.0");
        assertThat(version.get().getDefinitionJsonb()).contains("test");
    }

    @Test
    void shouldCheckVersionUniqueness() {
        boolean exists = workflowVersionRepository.existsByWorkflowIdAndVersion(
                testWorkflow.getId(), "1.0.0");
        
        assertThat(exists).isTrue();
        
        boolean notExists = workflowVersionRepository.existsByWorkflowIdAndVersion(
                testWorkflow.getId(), "2.0.0");
        
        assertThat(notExists).isFalse();
    }

    @Test
    void shouldFindByWorkflowIdOrderByVersionDesc() {
        // Create another version with different JSONB content
        WorkflowVersion version2 = WorkflowVersion.builder()
                .workflow(testWorkflow)
                .version("2.0.0")
                .definitionJsonb("{\"name\":\"test-v2\",\"version\":\"2.0.0\",\"states\":{\"start\":{\"type\":\"Initialize\",\"next\":\"process\"},\"process\":{\"type\":\"Task\",\"next\":\"end\"},\"end\":{\"type\":\"Success\"}}}")
                .isActive(false)
                .build();
        workflowVersionRepository.save(version2);

        List<WorkflowVersion> versions = workflowVersionRepository.findByWorkflowIdOrderByVersionDesc(testWorkflow.getId());
        
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).getVersion()).isEqualTo("2.0.0");
        assertThat(versions.get(1).getVersion()).isEqualTo("1.0.0");
        
        // Verify JSONB content is preserved
        assertThat(versions.get(0).getDefinitionJsonb()).contains("test-v2");
        assertThat(versions.get(0).getDefinitionJsonb()).contains("Initialize");
        assertThat(versions.get(1).getDefinitionJsonb()).contains("test");
        assertThat(versions.get(1).getDefinitionJsonb()).contains("Task");
    }

    @Test
    void shouldFindFirstByWorkflowIdOrderByVersionDesc() {
        Optional<WorkflowVersion> latestVersion = workflowVersionRepository
                .findFirstByWorkflowIdOrderByVersionDesc(testWorkflow.getId());
        
        assertThat(latestVersion).isPresent();
        assertThat(latestVersion.get().getVersion()).isEqualTo("1.0.0");
        assertThat(latestVersion.get().getDefinitionJsonb()).contains("test");
    }

    @Test
    void shouldFindActiveVersionByWorkflowId() {
        // Set version as active
        testVersion.setIsActive(true);
        workflowVersionRepository.save(testVersion);

        Optional<WorkflowVersion> activeVersion = workflowVersionRepository
                .findActiveVersionByWorkflowId(testWorkflow.getId());
        
        assertThat(activeVersion).isPresent();
        assertThat(activeVersion.get().getIsActive()).isTrue();
        assertThat(activeVersion.get().getDefinitionJsonb()).contains("test");
    }

    @Test
    void shouldHandleComplexJsonbData() {
        // Create a version with complex nested JSONB data
        String complexJsonb = "{" +
            "\"name\": \"complex-workflow\"," +
            "\"version\": \"3.0.0\"," +
            "\"description\": \"Complex workflow with nested structures\"," +
            "\"startAt\": \"initialize\"," +
            "\"states\": {" +
                "\"initialize\": {" +
                    "\"type\": \"Initialize\"," +
                    "\"next\": \"process\"," +
                    "\"parameters\": {" +
                        "\"timeout\": 300," +
                        "\"retries\": 3," +
                        "\"metadata\": {" +
                            "\"priority\": \"high\"," +
                            "\"tags\": [\"urgent\", \"critical\"]," +
                            "\"config\": {" +
                                "\"maxConcurrency\": 5," +
                                "\"enableLogging\": true" +
                            "}" +
                        "}" +
                    "}" +
                "}," +
                "\"process\": {" +
                    "\"type\": \"Task\"," +
                    "\"next\": \"choice\"," +
                    "\"inputPath\": \"$.data\"," +
                    "\"outputPath\": \"$.result\"" +
                "}," +
                "\"choice\": {" +
                    "\"type\": \"Choice\"," +
                    "\"choices\": [" +
                        "{" +
                            "\"condition\": \"success\"," +
                            "\"next\": \"success\"," +
                            "\"variable\": \"$.result.status\"" +
                        "}," +
                        "{" +
                            "\"condition\": \"failure\"," +
                            "\"next\": \"error\"," +
                            "\"variable\": \"$.result.error\"" +
                        "}" +
                    "]" +
                "}," +
                "\"success\": {" +
                    "\"type\": \"Success\"" +
                "}," +
                "\"error\": {" +
                    "\"type\": \"Fail\"," +
                    "\"error\": \"Workflow failed\"," +
                    "\"cause\": \"Processing error occurred\"" +
                "}" +
            "}" +
        "}";

        WorkflowVersion complexVersion = WorkflowVersion.builder()
                .workflow(testWorkflow)
                .version("3.0.0")
                .definitionJsonb(complexJsonb)
                .isActive(false)
                .build();
        
        WorkflowVersion savedComplexVersion = workflowVersionRepository.save(complexVersion);
        
        // Verify the complex JSONB data was stored correctly
        assertThat(savedComplexVersion.getId()).isNotNull();
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("complex-workflow");
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("3.0.0");
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("Initialize");
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("Task");
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("Choice");
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("timeout");
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("300");
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("priority");
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("high");
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("urgent");
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("critical");
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("maxConcurrency");
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("5");
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("enableLogging");
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("true");
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("inputPath");
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("$.data");
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("outputPath");
        assertThat(savedComplexVersion.getDefinitionJsonb()).contains("$.result");
        
        // Verify we can retrieve it back
        Optional<WorkflowVersion> retrievedVersion = workflowVersionRepository
                .findByWorkflowIdAndVersion(testWorkflow.getId(), "3.0.0");
        
        assertThat(retrievedVersion).isPresent();
        assertThat(retrievedVersion.get().getDefinitionJsonb()).isEqualTo(complexJsonb);
    }

    @Test
    void shouldHandleSpecialCharactersInJsonb() {
        // Test JSONB with special characters, unicode, and escape sequences
        String specialJsonb = "{" +
            "\"name\": \"special-chars-test\"," +
            "\"version\": \"4.0.0\"," +
            "\"description\": \"Test with special characters: \\\"quotes\\\", \\nnewlines\\n, \\ttabs\\t, \\u00E9 (é), \\u00F1 (ñ)\"," +
            "\"metadata\": {" +
                "\"special\": \"\\\"quoted\\\" string\"," +
                "\"multiline\": \"line1\\nline2\\nline3\"," +
                "\"unicode\": \"café mañana\"," +
                "\"path\": \"C:\\\\Users\\\\Test\\\\file.txt\"," +
                "\"regex\": \"\\d+\\s+\\w+\"" +
            "}" +
        "}";

        WorkflowVersion specialVersion = WorkflowVersion.builder()
                .workflow(testWorkflow)
                .version("4.0.0")
                .definitionJsonb(specialJsonb)
                .isActive(false)
                .build();
        
        WorkflowVersion savedSpecialVersion = workflowVersionRepository.save(specialVersion);
        
        // Verify special characters are preserved
        assertThat(savedSpecialVersion.getDefinitionJsonb()).contains("special-chars-test");
        assertThat(savedSpecialVersion.getDefinitionJsonb()).contains("quotes");
        assertThat(savedSpecialVersion.getDefinitionJsonb()).contains("newlines");
        assertThat(savedSpecialVersion.getDefinitionJsonb()).contains("tabs");
        assertThat(savedSpecialVersion.getDefinitionJsonb()).contains("café");
        assertThat(savedSpecialVersion.getDefinitionJsonb()).contains("mañana");
        assertThat(savedSpecialVersion.getDefinitionJsonb()).contains("C:\\Users\\Test\\file.txt");
        assertThat(savedSpecialVersion.getDefinitionJsonb()).contains("\\d+\\s+\\w+");
    }
}
