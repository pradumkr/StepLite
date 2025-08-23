# Workflow Execution Engine

This document describes the minimal execution engine that supports sequential states: Task, Success, and Fail.

## Overview

The execution engine provides a robust, scalable way to execute workflow definitions with support for:
- Sequential state transitions
- Task execution with handlers
- Idempotency
- Background processing
- Error handling and recovery
- Execution history tracking

## Architecture

### Core Components

1. **WorkflowExecutionService** - Manages workflow execution lifecycle
2. **WorkflowWorkerService** - Background worker that processes execution steps
3. **TaskRegistry** - Manages task handlers for different step types
4. **Repositories** - Data access layer for all execution-related entities

### State Types

- **Task** - Executes a handler and transitions to next state
- **Success** - Terminal state indicating successful completion
- **Fail** - Terminal state indicating failure

## APIs

### Start Workflow Execution

```http
POST /workflow-executions
Content-Type: application/json
Idempotency-Key: optional-unique-key

{
  "workflowName": "order-processing",
  "version": "1.0.0",
  "input": {
    "orderId": "order-123",
    "customerId": "customer-456",
    "amount": 100.50
  }
}
```

**Response:**
```json
{
  "id": 1,
  "executionId": "exec-1234567890-abc12345",
  "workflowName": "order-processing",
  "version": "1.0.0",
  "status": "RUNNING",
  "currentState": "start",
  "input": {
    "orderId": "order-123",
    "customerId": "customer-456",
    "amount": 100.50
  },
  "startedAt": "2024-01-01T10:00:00Z",
  "createdAt": "2024-01-01T10:00:00Z"
}
```

### Get Execution Status

```http
GET /workflow-executions/{id}
```

### Get Execution Step Details

```http
GET /workflow-executions/{id}/steps/{stepId}
```

## Task Handlers

### TaskHandler Interface

```java
public interface TaskHandler {
    TaskResult execute(Map<String, Object> input);
}
```

### TaskResult

```java
public class TaskResult {
    private Map<String, Object> output;
    private String errorType;
    private String errorMessage;
    private boolean success;
}
```

### Mock Task Handler

The engine includes a mock task handler for testing that supports:
- `sleepMs` - Simulate processing delay
- `simulateError` - Force error condition
- `errorType` - Custom error type
- `errorMessage` - Custom error message
- `shouldFail` - Conditional failure

## Workflow Definition Format

```yaml
name: "order-processing"
version: "1.0.0"
description: "Process customer orders"
startAt: "validate"
states:
  validate:
    type: "Task"
    next: "process"
    parameters:
      taskType: "orderService.validate"
      timeout: 5000
  process:
    type: "Task"
    next: "complete"
    parameters:
      taskType: "orderService.process"
  complete:
    type: "Success"
```

## Background Processing

### Worker Service

The `WorkflowWorkerService` runs in the background and:
- Polls the execution queue every second
- Processes queued steps using `FOR UPDATE SKIP LOCKED`
- Executes task handlers
- Manages state transitions
- Handles errors and retries

### Configuration

```yaml
workflow:
  worker:
    batch-size: 10
    stuck-step-timeout-minutes: 30
```

### Recovery

The worker service automatically recovers stuck steps:
- Detects steps stuck in RUNNING status for >30 minutes
- Resets them to PENDING and re-enqueues
- Runs every 5 minutes

## Concurrency & Safety

### Database Locks

- Uses `FOR UPDATE SKIP LOCKED` for queue processing
- Ensures only one worker processes each step
- Prevents double-execution across multiple app instances

### Transactions

- All worker operations wrapped in transactions
- If worker crashes mid-step, step remains RUNNING only if committed
- Otherwise, step stays queued for retry

### Idempotency

- Optional `Idempotency-Key` header
- If duplicate key provided, returns existing execution
- Keys expire after 24 hours

## Data Flow

1. **Input** → First step receives initial input
2. **Step Execution** → Task handler processes input
3. **Output Merge** → Step output merged with previous input (shallow merge)
4. **State Transition** → Next step receives merged data
5. **Termination** → Success/Fail states complete execution

## Example Usage

### 1. Register Workflow

```bash
curl -X POST http://localhost:8080/workflows \
  -H "Content-Type: application/x-yaml" \
  -d @sample-workflow.yaml
```

### 2. Start Execution

```bash
curl -X POST http://localhost:8080/workflow-executions \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-123-unique" \
  -d '{
    "workflowName": "order-processing",
    "input": {
      "orderId": "order-123",
      "amount": 100.50
    }
  }'
```

### 3. Monitor Progress

```bash
# Get execution status
curl http://localhost:8080/workflow-executions/1

# Get step details
curl http://localhost:8080/workflow-executions/1/steps/1
```

## Testing

### Unit Tests

- `WorkflowExecutionServiceTest` - Tests execution service
- `WorkflowVersionRepositoryTest` - Tests repository layer
- `JsonbIntegrationTest` - Tests JSONB functionality

### Integration Tests

- Uses Testcontainers for PostgreSQL and Redis
- Tests complete workflow execution flow
- Verifies JSONB data persistence and retrieval

## Scaling

### Multiple App Instances

- Database locks prevent double-execution
- Each instance can run workers independently
- Queue-based processing ensures load distribution

### Configuration

```yaml
# Increase worker batch size for higher throughput
workflow:
  worker:
    batch-size: 50

# Adjust polling frequency
# @Scheduled(fixedDelay = 500) # Poll every 500ms
```

## Monitoring

### Health Checks

- `/actuator/health` - Overall application health
- Database connection status
- Redis connection status

### Logging

- Execution lifecycle events
- Step transitions
- Error details with stack traces
- Performance metrics

### Metrics

- Execution count and duration
- Step success/failure rates
- Queue depth and processing times
- Worker activity

## Error Handling

### Step Failures

- Steps marked as FAILED with error message
- Execution marked as FAILED
- Error details stored in execution history
- No automatic retry (manual intervention required)

### System Failures

- Worker crashes handled gracefully
- Stuck steps automatically recovered
- Database transactions ensure consistency
- Idempotency prevents duplicate executions

## Future Enhancements

- **Retry Logic** - Automatic retry with exponential backoff
- **Parallel Execution** - Support for concurrent step execution
- **Conditional Transitions** - Dynamic next state based on step output
- **Sub-workflows** - Nested workflow execution
- **Event-driven** - Trigger workflows on external events
- **Scheduling** - Time-based workflow execution
