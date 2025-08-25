# Workflow Language Specification

## Overview

The Workflow Engine uses a YAML/JSON-based language to define workflows. This language is inspired by AWS Step Functions and provides a declarative way to describe complex business processes.

## Basic Structure

A workflow definition consists of:

```yaml
name: <workflow-name>
version: <version-string>
startAt: <initial-state-name>
states:
  <state-name>:
    type: <state-type>
    # ... state-specific configuration
```

### Required Fields

- **name**: Unique identifier for the workflow
- **version**: Version string (e.g., "1.0", "v2.1")
- **startAt**: Name of the first state to execute
- **states**: Map of state definitions

## State Types

### 1. Task State

Executes an external action or service call.

```yaml
state_name:
  type: Task
  resource: "serviceName.operation"
  next: "next_state_name"
  parameters:
    key1: "value1"
    key2: "value2"
  retry:
    maxAttempts: 3
    backoffMultiplier: 2
  timeout: 30
  catch:
    - errorType: "ErrorType"
      next: "error_state"
```

**Configuration Options:**
- **resource**: Service and operation identifier
- **next**: Next state to execute
- **parameters**: Input parameters for the task
- **retry**: Retry configuration
- **timeout**: Maximum execution time in seconds
- **catch**: Error handling configuration

### 2. Choice State

Provides conditional branching based on data values.

```yaml
choice_state:
  type: Choice
  choices:
    - condition:
        variable: "$.status"
        stringEquals: "approved"
      next: "approved_state"
    - condition:
        variable: "$.amount"
        numericGreaterThan: 1000
      next: "high_value_state"
  default: "default_state"
```

**Condition Types:**
- **stringEquals**: String equality comparison
- **stringLessThan**: String less than comparison
- **stringGreaterThan**: String greater than comparison
- **numericEquals**: Numeric equality comparison
- **numericLessThan**: Numeric less than comparison
- **numericGreaterThan**: Numeric greater than comparison
- **booleanEquals**: Boolean equality comparison
- **isPresent**: Check if field exists
- **isNull**: Check if field is null

### 3. Wait State

Pauses execution for a specified duration.

```yaml
wait_state:
  type: Wait
  seconds: 60
  next: "next_state"
```

**Alternative Time Formats:**
```yaml
wait_state:
  type: Wait
  timestamp: "2024-12-31T23:59:59Z"
  next: "next_state"
```

### 4. Success State

Terminal state indicating successful completion.

```yaml
success_state:
  type: Success
```

### 5. Fail State

Terminal state indicating workflow failure.

```yaml
fail_state:
  type: Fail
  error: "ErrorCode"
  cause: "Human readable error description"
```

## Advanced Features

### Retry Configuration

**⚠️ IMPORTANT: Retry mechanisms are NOT YET IMPLEMENTED. Only the configuration syntax is documented for future use.**

```yaml
retry:
  maxAttempts: 3
  backoffMultiplier: 2
  initialDelaySeconds: 1
  maxDelaySeconds: 60
```

**Note**: Currently, failed steps are marked as FAILED and execution stops. No automatic retries occur.

**Parameters (Future Implementation):**
- **maxAttempts**: Maximum number of retry attempts
- **backoffMultiplier**: Exponential backoff multiplier
- **initialDelaySeconds**: Initial delay before first retry
- **maxDelaySeconds**: Maximum delay between retries

### Error Handling (Catch Blocks)

```yaml
catch:
  - errorType: "ValidationError"
    next: "handle_validation_error"
  - errorType: "TimeoutError"
    next: "handle_timeout"
  - errorType: "*"
    next: "handle_any_error"
```

**Error Types:**
- **Specific Error**: Catch specific error types
- **Wildcard (*)**: Catch any error type
- **next**: State to transition to on error

### Timeout Configuration

```yaml
timeout: 30  # seconds
```

## Data Flow

### Input/Output Processing

- **Input**: Passed to the workflow execution
- **State Input**: Available as `$` in expressions
- **State Output**: Passed to the next state
- **Final Output**: Available when workflow completes

### Data References

```yaml
# Reference input data
variable: "$.orderId"

# Reference nested data
variable: "$.customer.address.city"

# Reference array elements
variable: "$.items[0].productId"
```

## Complete Example

```yaml
name: order_processing_workflow
version: 1.0
startAt: validate_order

states:
  validate_order:
    type: Task
    resource: "orderService.validate"
    next: check_inventory
    retry:
      maxAttempts: 3
      backoffMultiplier: 2
    catch:
      - errorType: "ValidationError"
        next: order_failed

  check_inventory:
    type: Task
    resource: "inventoryService.check"
    next: inventory_decision

  inventory_decision:
    type: Choice
    choices:
      - condition:
          variable: "$.inventoryAvailable"
          booleanEquals: true
        next: reserve_inventory
    default: notify_backorder

  reserve_inventory:
    type: Task
    resource: "inventoryService.reserve"
    next: process_payment
    timeout: 30
    retry:
      maxAttempts: 2

  process_payment:
    type: Task
    resource: "paymentService.charge"
    next: fulfill_order
    retry:
      maxAttempts: 2
    catch:
      - errorType: "PaymentError"
        next: payment_failed

  fulfill_order:
    type: Task
    resource: "fulfillmentService.ship"
    next: order_success

  notify_backorder:
    type: Task
    resource: "notificationService.backorder"
    next: order_failed

  payment_failed:
    type: Task
    resource: "paymentService.reverse"
    next: order_failed

  order_success:
    type: Success

  order_failed:
    type: Fail
    error: "OrderProcessingFailed"
    cause: "Order could not be completed"
```

## Best Practices

### 1. State Naming
- Use descriptive, lowercase names with underscores
- Avoid special characters except underscores
- Keep names under 50 characters

### 2. Error Handling
- Always provide catch blocks for Task states
- Use specific error types when possible
- Provide meaningful error messages

### 3. Retry Logic
- Set reasonable maxAttempts (2-5)
- Use exponential backoff for transient failures
- Consider business impact of retries

### 4. Timeout Configuration
- Set timeouts based on expected task duration
- Consider external service SLAs
- Use reasonable defaults (30-300 seconds)

### 5. Data Flow
- Minimize data passed between states
- Use clear, consistent data structures
- Document expected data format

## Validation Rules

### Workflow Level
- Must have exactly one startAt state
- All referenced states must exist
- No circular references allowed
- Must have at least one terminal state

### State Level
- Task states must have resource and next
- Choice states must have choices array
- Wait states must have time specification
- Terminal states cannot have next

### Data References
- JSONPath expressions must be valid
- Referenced fields should exist in input
- Array indices must be within bounds
