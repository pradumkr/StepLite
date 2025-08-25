# Integration Guide

## Overview

This guide explains how to integrate external systems with the Workflow Engine. It covers integration patterns, API usage, error handling, and best practices for building robust integrations.

## Integration Patterns

### 1. Synchronous Integration

Direct API calls for immediate workflow operations.

**Use Cases:**
- Starting workflows
- Getting execution status
- Cancelling executions
- Real-time monitoring

**Example:**
```java
// Start workflow execution
WorkflowExecutionRequest request = new WorkflowExecutionRequest();
request.setWorkflowName("order_processing_workflow");
request.setVersion("1.0");
request.setInput(orderData);

ResponseEntity<WorkflowExecutionResponse> response = 
    restTemplate.postForEntity("/workflow-executions", request, WorkflowExecutionResponse.class);
```

### 2. Asynchronous Integration

**⚠️ IMPORTANT: Webhook and event-driven features are NOT YET IMPLEMENTED in the workflow engine. The following examples show planned functionality.**

Event-driven integration using webhooks and callbacks (planned for future releases).

**Use Cases:**
- Long-running workflows
- Background processing
- Event notifications
- Status updates

**Example (Planned Implementation):**
```java
// Configure webhook for workflow completion
WebhookConfig config = new WebhookConfig();
config.setUrl("https://myapp.com/webhooks/workflow-completed");
config.setEvents(Arrays.asList("WORKFLOW_COMPLETED", "WORKFLOW_FAILED"));
config.setSecret("webhook-secret");

webhookService.registerWebhook(config);
```

### 3. Batch Integration

Processing multiple workflows in batches.

**Use Cases:**
- Bulk data processing
- Scheduled workflows
- Data migration
- Report generation

**Example:**
```java
// Process batch of orders
List<Order> orders = orderService.getPendingOrders();
List<WorkflowExecutionRequest> requests = orders.stream()
    .map(this::createExecutionRequest)
    .collect(Collectors.toList());

batchWorkflowService.executeBatch(requests);
```

## API Integration

### Authentication

Currently, the API does not require authentication. For production use:

```java
// Basic authentication
HttpHeaders headers = new HttpHeaders();
headers.setBasicAuth("username", "password");

HttpEntity<WorkflowExecutionRequest> entity = 
    new HttpEntity<>(request, headers);

ResponseEntity<WorkflowExecutionResponse> response = 
    restTemplate.exchange("/workflow-executions", HttpMethod.POST, entity, WorkflowExecutionResponse.class);
```

### Error Handling

Implement robust error handling for API calls:

```java
try {
    ResponseEntity<WorkflowExecutionResponse> response = 
        restTemplate.postForEntity("/workflow-executions", request, WorkflowExecutionResponse.class);
    
    if (response.getStatusCode() == HttpStatus.OK) {
        return response.getBody();
    } else {
        throw new WorkflowException("Unexpected response: " + response.getStatusCode());
    }
} catch (HttpClientErrorException e) {
    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new WorkflowNotFoundException("Workflow not found");
    } else if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
        throw new InvalidWorkflowException("Invalid workflow request: " + e.getResponseBodyAsString());
    } else {
        throw new WorkflowException("Workflow API error: " + e.getMessage());
    }
} catch (RestClientException e) {
    throw new WorkflowException("Connection error: " + e.getMessage());
}
```

### Retry Logic

**⚠️ IMPORTANT: Retry mechanisms are NOT YET IMPLEMENTED in the workflow engine. The following examples show how to implement retries in your client code when calling the workflow API.**

Implement retry logic for transient failures in your client application:

```java
@Retryable(value = {RestClientException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
public WorkflowExecutionResponse startWorkflowExecution(WorkflowExecutionRequest request) {
    // API call implementation
}

@Recover
public WorkflowExecutionResponse recoverStartExecution(Exception e, WorkflowExecutionRequest request) {
    log.error("Failed to start workflow execution after retries", e);
    throw new WorkflowException("Failed to start workflow execution", e);
}
```

## Workflow Definition Integration

### Dynamic Workflow Creation

Create workflows programmatically:

```java
public void createOrderWorkflow(String workflowName, String version) {
    WorkflowDefinitionDTO workflow = new WorkflowDefinitionDTO();
    workflow.setName(workflowName);
    workflow.setVersion(version);
    workflow.setDescription("Dynamic order processing workflow");
    workflow.setStartAt("validate_order");
    
    Map<String, StateDefinition> states = new HashMap<>();
    
    // Validate order state
    StateDefinition validateState = StateDefinition.builder()
        .type("Task")
        .resource("orderService.validate")
        .next("check_inventory")
        // Note: retry configuration is not yet implemented in the workflow engine
        .build();
    
    states.put("validate_order", validateState);
    
    // Add more states...
    
    workflow.setStates(states);
    
    // Register workflow
    workflowService.registerWorkflow(workflow);
}
```

### Workflow Templates

Use templates for common workflow patterns:

```java
public WorkflowDefinitionDTO createOrderWorkflowFromTemplate(OrderTemplate template) {
    String workflowYaml = loadWorkflowTemplate("order_processing_template.yaml");
    
    // Replace template variables
    workflowYaml = workflowYaml
        .replace("${WORKFLOW_NAME}", template.getName())
        .replace("${VERSION}", template.getVersion())
        .replace("${MAX_RETRIES}", String.valueOf(template.getMaxRetries()))
        .replace("${TIMEOUT}", String.valueOf(template.getTimeout()));
    
    // Parse YAML to DTO
    return yamlMapper.readValue(workflowYaml, WorkflowDefinitionDTO.class);
}
```

## Task Handler Integration

### Custom Task Handlers

Implement custom task handlers for business logic:

```java
@Component
public class OrderValidationTaskHandler implements TaskHandler {
    
    @Override
    public TaskResult execute(String resource, Map<String, Object> parameters) {
        try {
            String orderId = (String) parameters.get("orderId");
            Order order = orderService.getOrder(orderId);
            
            // Validate order
            ValidationResult result = orderValidator.validate(order);
            
            if (result.isValid()) {
                return TaskResult.success(Map.of(
                    "validated", true,
                    "validationTime", Instant.now(),
                    "orderStatus", "validated"
                ));
            } else {
                return TaskResult.failure("ValidationError", 
                    "Order validation failed: " + result.getErrors());
            }
        } catch (Exception e) {
            return TaskResult.failure("SystemError", 
                "System error during validation: " + e.getMessage());
        }
    }
    
    @Override
    public boolean supports(String resource) {
        return resource.equals("orderService.validate");
    }
}
```

### Task Handler Registry

Register custom task handlers:

```java
@Configuration
public class TaskHandlerConfig {
    
    @Bean
    public TaskRegistry taskRegistry(List<TaskHandler> handlers) {
        TaskRegistry registry = new TaskRegistry();
        
        for (TaskHandler handler : handlers) {
            registry.registerHandler(handler);
        }
        
        return registry;
    }
}
```

## Data Integration

### Input Data Transformation

Transform external data for workflow consumption:

```java
public Map<String, Object> transformOrderData(Order order) {
    Map<String, Object> workflowInput = new HashMap<>();
    
    workflowInput.put("orderId", order.getId());
    workflowInput.put("customerId", order.getCustomerId());
    workflowInput.put("totalAmount", order.getTotalAmount());
    workflowInput.put("items", order.getItems().stream()
        .map(this::transformOrderItem)
        .collect(Collectors.toList()));
    
    // Add business-specific data
    workflowInput.put("priority", calculatePriority(order));
    workflowInput.put("estimatedDelivery", calculateDeliveryDate(order));
    
    return workflowInput;
}
```

### Output Data Processing

Process workflow output data:

```java
public void processWorkflowOutput(Long executionId, Map<String, Object> output) {
    WorkflowExecution execution = executionService.getExecution(executionId);
    
    if ("order_success".equals(execution.getCurrentState())) {
        String orderId = (String) output.get("orderId");
        String trackingNumber = (String) output.get("trackingNumber");
        
        // Update order status
        orderService.updateOrderStatus(orderId, "fulfilled", trackingNumber);
        
        // Send notification
        notificationService.sendOrderFulfilledNotification(orderId, trackingNumber);
    }
}
```

## Monitoring and Observability

### Health Checks

Monitor workflow engine health:

```java
@Component
public class WorkflowHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        try {
            // Check database connectivity
            if (!databaseService.isHealthy()) {
                return Health.down()
                    .withDetail("database", "unhealthy")
                    .build();
            }
            
            // Check Redis connectivity
            if (!redisService.isHealthy()) {
                return Health.down()
                    .withDetail("redis", "unhealthy")
                    .build();
            }
            
            // Check worker service
            if (!workerService.isHealthy()) {
                return Health.down()
                    .withDetail("worker", "unhealthy")
                    .build();
            }
            
            return Health.up()
                .withDetail("database", "healthy")
                .withDetail("redis", "healthy")
                .withDetail("worker", "healthy")
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

### Metrics Collection

Collect workflow metrics:

```java
@Component
public class WorkflowMetrics {
    
    private final MeterRegistry meterRegistry;
    
    public WorkflowMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    public void recordWorkflowStarted(String workflowName) {
        Counter.builder("workflow.started")
            .tag("workflow", workflowName)
            .register(meterRegistry)
            .increment();
    }
    
    public void recordWorkflowCompleted(String workflowName, String status) {
        Counter.builder("workflow.completed")
            .tag("workflow", workflowName)
            .tag("status", status)
            .register(meterRegistry)
            .increment();
    }
    
    public void recordExecutionTime(String workflowName, long durationMs) {
        Timer.builder("workflow.execution.time")
            .tag("workflow", workflowName)
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }
}
```

## Event-Driven Integration

**⚠️ IMPORTANT: Webhook and event-driven features are NOT YET IMPLEMENTED in the workflow engine. The following examples show planned functionality.**

### Webhook Configuration (Planned Implementation)

Configure webhooks for workflow events (planned for future releases):

```java
@RestController
@RequestMapping("/webhooks")
public class WebhookController {
    
    @PostMapping("/workflow-events")
    public ResponseEntity<String> handleWorkflowEvent(
            @RequestBody WorkflowEvent event,
            @RequestHeader("X-Webhook-Signature") String signature) {
        
        // Verify webhook signature
        if (!webhookService.verifySignature(event, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }
        
        // Process workflow event
        switch (event.getType()) {
            case "WORKFLOW_STARTED":
                handleWorkflowStarted(event);
                break;
            case "WORKFLOW_COMPLETED":
                handleWorkflowCompleted(event);
                break;
            case "WORKFLOW_FAILED":
                handleWorkflowFailed(event);
                break;
            case "STATE_COMPLETED":
                handleStateCompleted(event);
                break;
        }
        
        return ResponseEntity.ok("Event processed");
    }
}
```

### Event Types

Supported workflow events:

```java
public enum WorkflowEventType {
    WORKFLOW_STARTED,
    WORKFLOW_COMPLETED,
    WORKFLOW_FAILED,
    WORKFLOW_CANCELLED,
    STATE_STARTED,
    STATE_COMPLETED,
    STATE_FAILED,
    // Note: STATE_RETRYING is not yet implemented
    WORKFLOW_TIMEOUT
}
```

## Security Considerations

### Input Validation

Validate all external inputs:

```java
@Component
public class WorkflowInputValidator {
    
    public void validateExecutionRequest(WorkflowExecutionRequest request) {
        if (request.getWorkflowName() == null || request.getWorkflowName().trim().isEmpty()) {
            throw new ValidationException("Workflow name is required");
        }
        
        if (request.getInput() == null) {
            throw new ValidationException("Input data is required");
        }
        
        // Validate input data size
        if (request.getInput().size() > MAX_INPUT_SIZE) {
            throw new ValidationException("Input data too large");
        }
        
        // Validate input data types
        validateInputDataTypes(request.getInput());
    }
    
    private void validateInputDataTypes(Map<String, Object> input) {
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            if (!isValidDataType(entry.getValue())) {
                throw new ValidationException("Invalid data type for field: " + entry.getKey());
            }
        }
    }
}
```

### Rate Limiting

Implement rate limiting for external integrations:

```java
@Configuration
public class RateLimitConfig {
    
    @Bean
    public RateLimiter workflowExecutionRateLimiter() {
        return RateLimiter.create(100.0); // 100 requests per second
    }
    
    @Bean
    public RateLimiter workflowRegistrationRateLimiter() {
        return RateLimiter.create(10.0); // 10 requests per second
    }
}
```

## Testing Integration

### Integration Tests

Test integration points:

```java
@SpringBootTest
@AutoConfigureTestDatabase
class WorkflowIntegrationTest {
    
    @Autowired
    private WorkflowExecutionService executionService;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void testWorkflowExecutionIntegration() {
        // Start workflow execution
        WorkflowExecutionRequest request = createTestRequest();
        ResponseEntity<WorkflowExecutionResponse> response = 
            restTemplate.postForEntity("/workflow-executions", request, WorkflowExecutionResponse.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus()).isEqualTo("RUNNING");
        
        // Wait for completion
        Long executionId = response.getBody().getExecutionId();
        await().atMost(30, TimeUnit.SECONDS)
            .until(() -> isExecutionCompleted(executionId));
        
        // Verify final state
        WorkflowExecutionResponse finalExecution = 
            executionService.getExecution(executionId);
        assertThat(finalExecution.getStatus()).isEqualTo("COMPLETED");
    }
}
```

### Mock Services

Mock external services for testing:

```java
@Configuration
@TestConfiguration
public class MockServiceConfig {
    
    @Bean
    @Primary
    public TaskHandler mockOrderService() {
        return new MockTaskHandler() {
            @Override
            public TaskResult execute(String resource, Map<String, Object> parameters) {
                if ("orderService.validate".equals(resource)) {
                    return TaskResult.success(Map.of("validated", true));
                }
                return TaskResult.failure("UnknownResource", "Resource not found");
            }
        };
    }
}
```

## Deployment Integration

### Docker Integration

Integrate with Docker deployments:

```dockerfile
FROM openjdk:17-jre-slim

COPY target/workflow-engine.jar app.jar
COPY config/application.yml config/

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Kubernetes Integration

Deploy to Kubernetes:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: workflow-engine
spec:
  replicas: 3
  selector:
    matchLabels:
      app: workflow-engine
  template:
    metadata:
      labels:
        app: workflow-engine
    spec:
      containers:
      - name: workflow-engine
        image: workflow-engine:latest
        ports:
        - containerPort: 8080
        env:
        - name: DB_HOST
          valueFrom:
            configMapKeyRef:
              name: workflow-config
              key: db.host
        - name: REDIS_HOST
          valueFrom:
            configMapKeyRef:
              name: workflow-config
              key: redis.host
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
```

## Best Practices

### 1. Error Handling
- Implement comprehensive error handling
- Use appropriate HTTP status codes
- Provide meaningful error messages
- Log errors for debugging

### 2. Performance
- Use connection pooling
- Implement caching where appropriate
- Monitor response times
- Use asynchronous processing for long operations

### 3. Security
- Validate all inputs
- Implement rate limiting
- Use HTTPS in production
- Implement proper authentication

### 4. Monitoring
- Monitor API response times
- Track error rates
- Monitor resource usage
- Set up alerts for critical issues

### 5. Testing
- Write comprehensive integration tests
- Test error scenarios
- Test performance under load
- Use realistic test data

## Support and Troubleshooting

### Common Issues

1. **Connection Timeouts**: Check network connectivity and firewall settings
2. **Authentication Errors**: Verify credentials and permissions
3. **Rate Limiting**: Implement exponential backoff for retries
4. **Data Validation Errors**: Check input data format and required fields

### Getting Help

- Check application logs for error details
- Monitor system metrics and health endpoints
- Review API documentation and examples
- Contact support team with specific error details
