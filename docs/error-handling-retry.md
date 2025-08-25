# Error Handling & Retry Guide

## Overview

Error handling and retry mechanisms are crucial for building robust, production-ready workflows. This guide covers the planned error handling strategies, retry configurations, and best practices for the Workflow Engine.

**⚠️ IMPORTANT: Retry mechanisms are NOT YET IMPLEMENTED. Only the infrastructure (database fields, DTOs) exists. The actual retry logic will be implemented in future releases.**

## Current Implementation Status

### What's Available Now
- ✅ **Error handling**: Basic error catching and logging
- ✅ **Error storage**: Error messages stored in execution history
- ✅ **Status tracking**: Steps marked as FAILED on errors
- ✅ **Infrastructure**: Database fields for retry configuration

### What's NOT Implemented Yet
- ❌ **Automatic retries**: Failed steps are not automatically retried
- ❌ **Retry logic**: No retry count increment or backoff calculation
- ❌ **Re-queuing**: Failed steps are not re-queued for retry
- ❌ **Exponential backoff**: No delay calculation between retries
- ❌ **Retry limits**: No enforcement of max retry attempts

## Error Handling Architecture

### Current Error Handling Layers

The Workflow Engine currently implements basic error handling:

1. **Task Level**: Individual task error handling (no retry)
2. **State Level**: State-specific error handling with catch blocks
3. **Workflow Level**: Global error handling and fallback strategies
4. **System Level**: Infrastructure and system error handling

### Current Error Flow

```
Task Execution → Error Occurs → Mark as Failed → Log Error → Stop Execution
     ↓              ↓              ↓            ↓            ↓
  Execute      Error Type      Update      Record in      No
  Task         Detection       Status      History      Retry
```

## Planned Retry Mechanisms

### Retry Configuration (Future Implementation)

Retry mechanisms will automatically attempt to re-execute failed tasks with configurable backoff strategies.

#### Basic Retry Configuration

```yaml
retry:
  maxAttempts: 3
  backoffMultiplier: 2
  initialDelaySeconds: 1
  maxDelaySeconds: 60
```

#### Advanced Retry Configuration

```yaml
retry:
  maxAttempts: 5
  backoffMultiplier: 2
  initialDelaySeconds: 1
  maxDelaySeconds: 120
  jitter: true
  retryOnErrors:
    - "ConnectionError"
    - "TimeoutError"
    - "RateLimitError"
  skipRetryOnErrors:
    - "ValidationError"
    - "AuthenticationError"
```

### Retry Parameters (Future Implementation)

#### Required Parameters

- **maxAttempts**: Maximum number of retry attempts (including initial attempt)
- **backoffMultiplier**: Multiplier for exponential backoff

#### Optional Parameters

- **initialDelaySeconds**: Initial delay before first retry (default: 1)
- **maxDelaySeconds**: Maximum delay between retries (default: 300)
- **jitter**: Add randomness to retry delays (default: false)
- **retryOnErrors**: Specific error types to retry on
- **skipRetryOnErrors**: Error types to skip retrying

## Current Error Handling with Catch Blocks

### Catch Block Configuration

Catch blocks handle errors that occur during task execution and provide recovery paths.

#### Basic Catch Block

```yaml
catch:
  - errorType: "ValidationError"
    next: "handle_validation_error"
```

#### Advanced Catch Block

```yaml
catch:
  - errorType: "PaymentError"
    next: "handle_payment_failure"
  - errorType: "AuthenticationError"
    next: "handle_auth_failure"
  - errorType: "RateLimitError"
    next: "wait_and_retry"
  - errorType: "*"
    next: "handle_critical_error"
```

### Current Error Recovery Strategies

Since retries are not implemented, use these alternative strategies:

#### 1. Alternative Paths

Route to alternative processing paths on specific errors:

```yaml
check_inventory:
  type: Task
  resource: "inventoryService.check"
  next: "inventory_decision"
  catch:
    - errorType: "InventoryError"
      next: "use_alternative_warehouse"
    - errorType: "ConnectionError"
      next: "use_cached_inventory"
```

#### 2. Graceful Degradation

Continue processing with reduced functionality:

```yaml
enhanced_validation:
  type: Task
  resource: "validationService.enhanced"
  next: "process_order"
  catch:
    - errorType: "ValidationError"
      next: "basic_validation"
    - errorType: "*"
      next: "skip_validation"
```

#### 3. Compensation Actions

Execute compensation actions to undo previous operations:

```yaml
reserve_inventory:
  type: Task
  resource: "inventoryService.reserve"
  next: "process_payment"
  catch:
    - errorType: "CriticalError"
      next: "compensate_previous_actions"
```

## Current Error Handling Examples

### 1. Basic Error Handling

```yaml
validate_order:
  type: Task
  resource: "orderService.validate"
  next: "check_inventory"
  catch:
    - errorType: "ValidationError"
      next: "handle_validation_error"
    - errorType: "*"
      next: "handle_system_error"
```

### 2. Error-Specific Handling

```yaml
process_payment:
  type: Task
  resource: "paymentService.charge"
  next: "fulfill_order"
  catch:
    - errorType: "PaymentError"
      next: "handle_payment_failure"
    - errorType: "AuthenticationError"
      next: "handle_auth_failure"
    - errorType: "RateLimitError"
      next: "wait_and_retry"
    - errorType: "*"
      next: "handle_critical_error"
```

### 3. Fallback Error Handling

Provide fallback error handling for unhandled errors:

```yaml
catch:
  - errorType: "SpecificError"
    next: "handle_specific_error"
  - errorType: "*"
    next: "handle_generic_error"
```

## Current Best Practices

### 1. Error Handling

#### Always Handle Errors

```yaml
# Good: Always has error handling
catch:
  - errorType: "SpecificError"
    next: "handle_specific_error"
  - errorType: "*"
    next: "handle_generic_error"

# Avoid: No error handling
# Missing catch blocks
```

#### Provide Clear Error Descriptions

```yaml
# Provide clear error descriptions
fail_state:
  type: Fail
  error: "OrderProcessingFailed"
  cause: "Order validation failed: customer information incomplete"
```

### 2. Alternative Strategies (Since Retries Don't Work)

#### Use Multiple Choice States

```yaml
# Instead of retrying, use alternative paths
check_service:
  type: Task
  resource: "service.check"
  next: "service_available"
  catch:
    - errorType: "ServiceError"
      next: "use_alternative_service"
    - errorType: "*"
      next: "use_basic_processing"
```

#### Pre-compute Fallback Values

```yaml
# Use tasks to compute fallback values
compute_fallback:
  type: Task
  resource: "fallbackService.compute"
  next: "use_fallback"
  catch:
    - errorType: "*"
      next: "use_default_values"
```

### 3. Timeout Management

#### Realistic Timeout Values

```yaml
# Set timeouts based on expected duration
timeout: 30   # Quick operations
timeout: 120  # Medium operations
timeout: 600  # Long operations
```

#### Timeout Error Handling

```yaml
# Always handle timeout errors
catch:
  - errorType: "TimeoutError"
    next: "handle_timeout"
```

## Common Error Scenarios

### 1. Network Connectivity Issues

```yaml
network_task:
  type: Task
  resource: "externalService.call"
  next: "process_result"
  catch:
    - errorType: "ConnectionError"
      next: "handle_network_failure"
    - errorType: "*"
      next: "handle_system_error"
```

### 2. External Service Failures

```yaml
external_service_task:
  type: Task
  resource: "paymentService.charge"
  next: "fulfill_order"
  catch:
    - errorType: "PaymentError"
      next: "handle_payment_failure"
    - errorType: "RateLimitError"
      next: "wait_and_retry"
```

### 3. Data Validation Failures

```yaml
validation_task:
  type: Task
  resource: "validationService.validate"
  next: "process_data"
  catch:
    - errorType: "ValidationError"
      next: "handle_validation_error"
    - errorType: "*"
      next: "handle_system_error"
```

### 4. Resource Exhaustion

```yaml
resource_intensive_task:
  type: Task
  resource: "processingService.heavy"
  next: "next_state"
  timeout: 300
  catch:
    - errorType: "TimeoutError"
      next: "handle_resource_exhaustion"
    - errorType: "*"
      next: "handle_system_error"
```

## Testing Error Scenarios

### 1. Error Handling Testing

Test error handling with different error types:

```yaml
test_error_handling:
  startAt: test_task
  
  states:
    test_task:
      type: Task
      resource: "testService.throwError"
      next: "success"
      catch:
        - errorType: "TestError"
          next: "handle_test_error"
    
    handle_test_error:
      type: Task
      resource: "testService.handleError"
      next: "success"
```

### 2. Timeout Testing

Test timeout handling with slow operations:

```yaml
test_timeout:
  startAt: slow_task
  
  states:
    slow_task:
      type: Task
      resource: "testService.slowOperation"
      timeout: 5
      next: "success"
      catch:
        - errorType: "TimeoutError"
          next: "handle_timeout"
```

## Future Implementation Plan

### Phase 1: Basic Retry Logic
- Implement retry count increment on failure
- Add retry limit enforcement
- Basic exponential backoff calculation

### Phase 2: Advanced Retry Features
- Conditional retry based on error types
- Jitter and randomization
- Retry state management

### Phase 3: Retry Monitoring
- Retry metrics and observability
- Retry history tracking
- Retry performance optimization

## Conclusion

**Current Status**: The Workflow Engine has basic error handling but **no automatic retry mechanisms**. Failed steps are marked as failed and execution stops.

**Workaround**: Use alternative paths, fallback strategies, and multiple choice states to handle errors gracefully.

**Future**: Retry mechanisms will be implemented in phases, starting with basic retry logic and progressing to advanced features.

For now, focus on robust error handling using catch blocks and alternative processing paths rather than relying on automatic retries.
