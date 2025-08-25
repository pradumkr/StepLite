# Wait States Guide

## Overview

Wait states provide time-based delays in workflows, allowing you to pause execution for specified durations or until specific timestamps. This guide covers the various wait state configurations, use cases, and best practices for implementing time-based workflow logic.

## Wait State Fundamentals

### Basic Structure

A Wait state pauses workflow execution for a specified time period.

```yaml
wait_state:
  type: Wait
  seconds: 60
  next: "next_state"
```

### Wait State Components

#### Required Elements

- **type**: Must be "Wait"
- **next**: Next state to execute after waiting

#### Time Specification

Choose one of the following time formats:

- **seconds**: Relative time delay in seconds
- **timestamp**: Absolute time to wait until

## Wait State Types

### 1. Relative Time Wait

Pause execution for a specified number of seconds.

#### Basic Relative Wait

```yaml
simple_wait:
  type: Wait
  seconds: 30
  next: "continue_processing"
```

#### Long Duration Wait

```yaml
long_wait:
  type: Wait
  seconds: 3600  # 1 hour
  next: "check_status"
```

#### Short Duration Wait

```yaml
short_wait:
  type: Wait
  seconds: 5     # 5 seconds
  next: "retry_operation"
```

### 2. Absolute Time Wait

Pause execution until a specific timestamp is reached.

#### ISO 8601 Timestamp

```yaml
scheduled_wait:
  type: Wait
  timestamp: "2024-12-31T23:59:59Z"
  next: "execute_scheduled_task"
```

#### Timezone-Aware Timestamp

```yaml
timezone_wait:
  type: Wait
  timestamp: "2024-01-15T09:00:00+00:00"
  next: "start_business_hours"
```

#### Future Date Wait

```yaml
future_wait:
  type: Wait
  timestamp: "2024-06-01T00:00:00Z"
  next: "monthly_processing"
```

## Common Use Cases

### 1. Rate Limiting

Implement delays to respect API rate limits.

```yaml
rate_limited_api_call:
  type: Task
  resource: "externalService.call"
  next: "wait_for_rate_limit"
  catch:
    - errorType: "RateLimitError"
      next: "wait_for_rate_limit"

wait_for_rate_limit:
  type: Wait
  seconds: 60  # Wait 1 minute
  next: "retry_api_call"

retry_api_call:
  type: Task
  resource: "externalService.call"
  next: "process_result"
```

### 2. Scheduled Processing

Wait for specific times to execute scheduled tasks.

```yaml
wait_for_business_hours:
  type: Wait
  timestamp: "2024-01-15T09:00:00Z"
  next: "start_business_processing"

start_business_processing:
  type: Task
  resource: "businessService.start"
  next: "continue_processing"
```

### 3. Retry Delays

**⚠️ IMPORTANT: Retry mechanisms are NOT YET IMPLEMENTED in the workflow engine. The following examples show planned functionality.**

Implement delays between retry attempts.

```yaml
failed_operation:
  type: Task
  resource: "service.operation"
  next: "success"
  catch:
    - errorType: "ServiceError"
      next: "wait_before_retry"

wait_before_retry:
  type: Wait
  seconds: 30
  next: "retry_operation"

retry_operation:
  type: Task
  resource: "service.operation"
  next: "success"
```

### 4. Approval Workflows

Wait for human approval or external events.

```yaml
request_approval:
  type: Task
  resource: "approvalService.request"
  next: "wait_for_approval"

wait_for_approval:
  type: Wait
  seconds: 86400  # Wait 24 hours
  next: "check_approval_status"

check_approval_status:
  type: Task
  resource: "approvalService.check"
  next: "approval_decision"
```

### 5. Batch Processing

Wait for batch windows or processing cycles.

```yaml
prepare_batch:
  type: Task
  resource: "batchService.prepare"
  next: "wait_for_batch_window"

wait_for_batch_window:
  type: Wait
  timestamp: "2024-01-15T02:00:00Z"  # 2 AM batch window
  next: "process_batch"

process_batch:
  type: Task
  resource: "batchService.process"
  next: "batch_complete"
```

## Advanced Wait Patterns

### 1. Dynamic Wait Duration

Calculate wait duration based on workflow data.

```yaml
calculate_wait_duration:
  type: Task
  resource: "waitService.calculateDuration"
  next: "dynamic_wait"
  parameters:
    baseDelay: 60
    multiplier: "$.priorityMultiplier"

dynamic_wait:
  type: Wait
  seconds: "$.calculatedWaitDuration"
  next: "continue_processing"
```

### 2. Conditional Wait

Wait only under certain conditions.

```yaml
check_wait_condition:
  type: Choice
  choices:
    - condition:
        variable: "$.requiresDelay"
        booleanEquals: true
      next: "wait_delay"
  default: "continue_processing"

wait_delay:
  type: Wait
  seconds: 300  # 5 minutes
  next: "continue_processing"
```

### 3. Progressive Wait

Increase wait duration with each attempt.

```yaml
progressive_retry:
  type: Task
  resource: "service.operation"
  next: "success"
  catch:
    - errorType: "ServiceError"
      next: "calculate_progressive_wait"

calculate_progressive_wait:
  type: Task
  resource: "waitService.calculateProgressive"
  next: "progressive_wait"
  parameters:
    attemptNumber: "$.retryCount"
    baseDelay: 60

progressive_wait:
  type: Wait
  seconds: "$.calculatedDelay"
  next: "retry_operation"
```

### 4. Wait with Timeout

Implement maximum wait duration.

```yaml
wait_with_timeout:
  type: Wait
  seconds: 3600  # Wait up to 1 hour
  next: "check_wait_result"
  timeout: 7200  # Maximum 2 hours total

check_wait_result:
  type: Task
  resource: "waitService.checkResult"
  next: "process_result"
```

## Wait State Configuration

### Time Format Options

#### Relative Time (Seconds)

```yaml
# Simple seconds
seconds: 60

# Long duration
seconds: 86400  # 24 hours

# Short duration
seconds: 1      # 1 second

# Fractional seconds (if supported)
seconds: 0.5    # 500 milliseconds
```

#### Absolute Time (Timestamp)

```yaml
# ISO 8601 format
timestamp: "2024-12-31T23:59:59Z"

# With timezone
timestamp: "2024-01-15T09:00:00+00:00"

# Date only (assumes midnight)
timestamp: "2024-06-01T00:00:00Z"

# Specific time
timestamp: "2024-01-15T14:30:00Z"
```

### Wait State Parameters

#### Basic Parameters

```yaml
wait_state:
  type: Wait
  seconds: 60
  next: "next_state"
  comment: "Wait for 1 minute before retry"
```

#### Advanced Parameters

```yaml
advanced_wait:
  type: Wait
  seconds: 300
  next: "next_state"
  comment: "Wait 5 minutes for external system"
  metadata:
    reason: "rate_limiting"
    category: "retry_delay"
    businessJustification: "Respect external API limits"
```

## Wait State Execution Flow

### Execution Timeline

```
Start Wait → Calculate Wait Duration → Pause Execution → Resume → Next State
     ↓              ↓                    ↓              ↓        ↓
  Current      Time Calculation    Wait Period    Resume    Execute
  State       (Relative/Absolute)  (Sleep)       Execution Next State
```

### Wait State Behavior

#### During Wait

- **Execution Paused**: Workflow execution is suspended
- **Resource Usage**: Minimal resource consumption
- **State Persistence**: Wait state is persisted in database
- **Resume Capability**: Can be resumed manually or automatically

#### Wait Completion

- **Automatic Resume**: Execution continues automatically
- **State Transition**: Moves to the specified next state
- **Data Preservation**: All workflow data is maintained
- **Timing Accuracy**: Resume occurs at or after specified time

## Best Practices

### 1. Wait Duration Selection

#### Appropriate Wait Times

```yaml
# Quick operations
seconds: 5      # 5 seconds

# Standard operations
seconds: 60     # 1 minute

# Long operations
seconds: 3600   # 1 hour

# Very long operations
seconds: 86400  # 24 hours
```

#### Business Context Considerations

```yaml
# User experience
seconds: 30     # Short enough for user patience

# System constraints
seconds: 300    # Respect rate limits

# Business rules
seconds: 3600   # Wait for business hours
```

### 2. Wait State Naming

#### Descriptive Names

```yaml
# Good names
wait_for_approval
wait_before_retry
wait_for_business_hours
wait_for_batch_window

# Avoid names
wait
delay
pause
```

#### Consistent Naming Convention

```yaml
# Use consistent prefix
wait_for_<event>
wait_before_<action>
wait_until_<time>
```

### 3. Error Handling

#### Wait State Error Handling

```yaml
wait_state:
  type: Wait
  seconds: 60
  next: "next_state"
  catch:
    - errorType: "WaitError"
      next: "handle_wait_error"
```

#### Timeout Handling

```yaml
wait_with_timeout:
  type: Wait
  seconds: 3600
  next: "check_result"
  timeout: 7200
  catch:
    - errorType: "TimeoutError"
      next: "handle_wait_timeout"
```

### 4. Monitoring and Observability

#### Wait State Metrics

```yaml
# Track wait durations
wait_metrics:
  - metric: "workflow.wait.duration"
    tags:
      - "workflow_name"
      - "state_name"
      - "wait_type"
  
  - metric: "workflow.wait.count"
    tags:
      - "workflow_name"
      - "wait_reason"
```

#### Wait State Logging

```yaml
# Log wait state activities
logging:
  level:
    com.freightmate.workflow.wait: INFO
  
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} [%X{executionId}] - Wait state: %msg%n"
```

## Common Wait Patterns

### 1. Exponential Backoff

**⚠️ IMPORTANT: Retry mechanisms are NOT YET IMPLEMENTED in the workflow engine. The following examples show planned functionality.**

Implement exponential backoff for retry scenarios.

```yaml
exponential_backoff:
  type: Task
  resource: "service.operation"
  next: "success"
  catch:
    - errorType: "ServiceError"
      next: "calculate_backoff"

calculate_backoff:
  type: Task
  resource: "backoffService.calculate"
  next: "wait_backoff"
  parameters:
    attemptNumber: "$.retryCount"
    baseDelay: 60
    maxDelay: 3600

wait_backoff:
  type: Wait
  seconds: "$.calculatedBackoff"
  next: "retry_operation"
```

### 2. Scheduled Execution

Wait for specific times to execute tasks.

```yaml
scheduled_workflow:
  startAt: wait_for_schedule
  
  states:
    wait_for_schedule:
      type: Wait
      timestamp: "2024-01-15T09:00:00Z"
      next: "start_scheduled_task"
    
    start_scheduled_task:
      type: Task
      resource: "scheduledService.start"
      next: "complete_scheduled_task"
    
    complete_scheduled_task:
      type: Success
```

### 3. Approval Workflow

Wait for human approval or external events.

```yaml
approval_workflow:
  startAt: request_approval
  
  states:
    request_approval:
      type: Task
      resource: "approvalService.request"
      next: "wait_for_approval"
    
    wait_for_approval:
      type: Wait
      seconds: 86400  # Wait 24 hours
      next: "check_approval"
    
    check_approval:
      type: Task
      resource: "approvalService.check"
      next: "approval_decision"
    
    approval_decision:
      type: Choice
      choices:
        - condition:
            variable: "$.approvalStatus"
            stringEquals: "approved"
          next: "process_approved"
        - condition:
            variable: "$.approvalStatus"
            stringEquals: "rejected"
          next: "handle_rejected"
        - condition:
            variable: "$.approvalStatus"
            stringEquals: "pending"
          next: "wait_for_approval"
      default: "handle_unknown_status"
```

### 4. Batch Processing

Wait for batch windows or processing cycles.

```yaml
batch_workflow:
  startAt: prepare_batch
  
  states:
    prepare_batch:
      type: Task
      resource: "batchService.prepare"
      next: "wait_for_batch_window"
    
    wait_for_batch_window:
      type: Wait
      timestamp: "2024-01-15T02:00:00Z"
      next: "process_batch"
    
    process_batch:
      type: Task
      resource: "batchService.process"
      next: "batch_complete"
    
    batch_complete:
      type: Success
```

### 5. Rate Limiting

Implement delays to respect API rate limits.

```yaml
rate_limited_workflow:
  startAt: api_call
  
  states:
    api_call:
      type: Task
      resource: "externalApi.call"
      next: "success"
      catch:
        - errorType: "RateLimitError"
          next: "wait_for_rate_limit"
    
    wait_for_rate_limit:
      type: Wait
      seconds: 60
      next: "retry_api_call"
    
    retry_api_call:
      type: Task
      resource: "externalApi.call"
      next: "success"
```

## Performance Considerations

### 1. Wait State Efficiency

#### Resource Usage

- **Memory**: Minimal memory usage during wait
- **CPU**: No CPU consumption during wait
- **Database**: Periodic state persistence
- **Network**: No network activity during wait

#### Scalability

```yaml
# Efficient for many concurrent waits
wait_state:
  type: Wait
  seconds: 3600
  next: "next_state"
  # Minimal resource impact
```

### 2. Wait Duration Optimization

#### Appropriate Wait Times

```yaml
# Too short: May cause unnecessary retries
seconds: 1

# Too long: May delay processing unnecessarily
seconds: 86400

# Optimal: Balance between retry frequency and system load
seconds: 60
```

#### Dynamic Wait Calculation

```yaml
# Calculate optimal wait time based on context
calculate_optimal_wait:
  type: Task
  resource: "waitService.calculateOptimal"
  next: "optimal_wait"
  parameters:
    errorType: "$.lastError"
    retryCount: "$.retryCount"
    systemLoad: "$.currentSystemLoad"

optimal_wait:
  type: Wait
  seconds: "$.optimalWaitDuration"
  next: "retry_operation"
```

## Testing Wait States

### 1. Unit Testing

Test wait state behavior in isolation.

```yaml
test_wait_workflow:
  startAt: test_wait
  
  states:
    test_wait:
      type: Wait
      seconds: 5
      next: "verify_wait"
    
    verify_wait:
      type: Task
      resource: "testService.verifyWait"
      next: "success"
      parameters:
        expectedWaitDuration: 5
```

### 2. Integration Testing

Test wait states in workflow context.

```yaml
test_wait_integration:
  startAt: start_process
  
  states:
    start_process:
      type: Task
      resource: "testService.start"
      next: "wait_for_event"
    
    wait_for_event:
      type: Wait
      seconds: 10
      next: "check_event"
    
    check_event:
      type: Task
      resource: "testService.checkEvent"
      next: "success"
```

### 3. Performance Testing

Test wait state performance under load.

```yaml
test_wait_performance:
  startAt: concurrent_wait
  
  states:
    concurrent_wait:
      type: Wait
      seconds: 60
      next: "measure_performance"
    
    measure_performance:
      type: Task
      resource: "testService.measurePerformance"
      next: "success"
```

## Common Pitfalls

### 1. Excessive Wait Times

Avoid unnecessarily long wait durations.

```yaml
# Problem: Too long wait
wait_state:
  type: Wait
  seconds: 604800  # 1 week - too long for most use cases

# Solution: Reasonable wait time
wait_state:
  type: Wait
  seconds: 3600    # 1 hour - more appropriate
```

### 2. Missing Error Handling

Handle wait state errors appropriately.

```yaml
# Problem: No error handling
wait_state:
  type: Wait
  seconds: 60
  next: "next_state"

# Solution: Include error handling
wait_state:
  type: Wait
  seconds: 60
  next: "next_state"
  catch:
    - errorType: "WaitError"
      next: "handle_wait_error"
```

### 3. Inappropriate Wait Types

Choose the right wait type for your use case.

```yaml
# Problem: Using absolute time for relative delay
wait_state:
  type: Wait
  timestamp: "2024-01-15T10:00:00Z"  # Absolute time
  next: "next_state"

# Solution: Use relative time for simple delays
wait_state:
  type: Wait
  seconds: 3600  # Relative time - 1 hour
  next: "next_state"
```

## Monitoring and Alerting

### 1. Wait State Monitoring

Monitor wait state performance and behavior.

```yaml
# Wait state metrics
monitoring:
  metrics:
    - name: "workflow.wait.duration"
      description: "Wait state duration"
      unit: "seconds"
    
    - name: "workflow.wait.count"
      description: "Number of wait states executed"
      unit: "count"
```

### 2. Wait State Alerting

Set up alerts for wait state issues.

```yaml
# Wait state alerts
alerting:
  rules:
    - name: "LongWaitDuration"
      condition: "workflow.wait.duration > 3600"
      severity: "warning"
      message: "Wait state duration exceeds 1 hour"
    
    - name: "ExcessiveWaitCount"
      condition: "workflow.wait.count > 100"
      severity: "critical"
      message: "Excessive number of wait states"
```

### 3. Wait State Dashboards

Create dashboards for wait state visibility.

```yaml
# Wait state dashboard
dashboard:
  title: "Workflow Wait States"
  panels:
    - title: "Wait Duration Distribution"
      type: "histogram"
      metric: "workflow.wait.duration"
    
    - title: "Wait State Count by Workflow"
      type: "bar"
      metric: "workflow.wait.count"
      groupBy: "workflow_name"
```

## Conclusion

Wait states provide essential time-based control in workflows, enabling rate limiting, scheduled processing, approval workflows, and planned retry logic. By understanding the various wait patterns and best practices, you can implement effective time-based workflow logic.

## Summary

Wait states are a powerful feature for controlling workflow timing and flow. Key benefits include:

- **Time-based control** for workflow progression
- **Rate limiting** and throttling capabilities
- **Scheduled execution** at specific times
- **Approval workflows** with human intervention
- **Planned retry logic** (when implemented in future releases)
- **Common patterns** include scheduled execution and approval workflows
