# State Types Guide

## Overview

The Workflow Engine supports several state types that define the behavior and flow of workflows. Each state type has specific configuration options and behaviors.

## State Type Categories

### 1. Execution States
- **Task**: Execute external actions
- **Wait**: Pause execution for time-based delays

### 2. Control Flow States
- **Choice**: Conditional branching logic
- **Success**: Successful completion
- **Fail**: Error termination

## Task State

The Task state executes external actions or service calls. It's the most commonly used state type for business logic execution.

### Basic Configuration

```yaml
state_name:
  type: Task
  resource: "serviceName.operation"
  next: "next_state_name"
```

### Full Configuration

```yaml
state_name:
  type: Task
  resource: "orderService.validate"
  next: "check_inventory"
  parameters:
    orderId: "ORD-12345"
    customerId: "CUST-001"
    validateAddress: true
  retry:
    maxAttempts: 3
    backoffMultiplier: 2
    initialDelaySeconds: 1
    maxDelaySeconds: 60
  timeout: 30
  catch:
    - errorType: "ValidationError"
      next: "handle_validation_error"
    - errorType: "TimeoutError"
      next: "handle_timeout"
    - errorType: "*"
      next: "handle_any_error"
```

### Configuration Options

#### Required Fields

- **type**: Must be "Task"
- **resource**: Service and operation identifier
- **next**: Next state to execute on success

#### Optional Fields

- **parameters**: Input parameters for the task
- **retry**: Retry configuration
- **timeout**: Maximum execution time in seconds
- **catch**: Error handling configuration

### Resource Format

The resource field follows the pattern: `"serviceName.operation"`

**Examples:**
- `"orderService.validate"` - Order validation service
- `"paymentService.charge"` - Payment processing
- `"inventoryService.check"` - Inventory checking
- `"notificationService.send"` - Notification sending

### Parameters

Parameters are passed to the task handler as input data:

```yaml
parameters:
  orderId: "ORD-12345"
  customerId: "CUST-001"
  amount: 99.99
  currency: "USD"
  metadata:
    source: "web"
    priority: "high"
```

### Retry Configuration

**⚠️ IMPORTANT: Retry mechanisms are NOT YET IMPLEMENTED. Only the configuration syntax is documented for future use.**

Configure automatic retry behavior for transient failures (when implemented):

```yaml
retry:
  maxAttempts: 3              # Maximum retry attempts
  backoffMultiplier: 2         # Exponential backoff multiplier
  initialDelaySeconds: 1       # Initial delay before first retry
  maxDelaySeconds: 60          # Maximum delay between retries
```

**Note**: Currently, failed steps are marked as FAILED and execution stops. No automatic retries occur.

**Planned Retry Logic (Future Implementation):**
1. First retry: 1 second delay
2. Second retry: 2 seconds delay
3. Third retry: 4 seconds delay
4. Maximum delay capped at 60 seconds

### Timeout Configuration

Set maximum execution time for tasks:

```yaml
timeout: 30  # seconds
```

**Timeout Behavior:**
- Task execution is interrupted after timeout
- TimeoutError is thrown
- Catch blocks can handle timeout errors
- Default timeout is 300 seconds if not specified

### Error Handling (Catch Blocks)

Handle different types of errors with specific recovery paths:

```yaml
catch:
  - errorType: "ValidationError"
    next: "handle_validation_error"
  - errorType: "TimeoutError"
    next: "handle_timeout"
  - errorType: "PaymentError"
    next: "handle_payment_error"
  - errorType: "*"
    next: "handle_any_error"
```

**Error Types:**
- **Specific Errors**: Handle specific error types
- **Wildcard (*)**: Catch any unhandled errors
- **Order Matters**: First matching error handler is used

### Task Execution Flow

```
Start Task → Execute Task Handler → Process Result → Handle Success/Error → Transition
     ↓              ↓                    ↓              ↓              ↓
  Current      Task-specific       Success/Error   Next State    Update State
  State       Processing           Handling        Selection     Machine
```

### Example Task States

#### 1. Simple Order Validation

```yaml
validate_order:
  type: Task
  resource: "orderService.validate"
  next: "check_inventory"
  parameters:
    orderId: "$.orderId"
    customerId: "$.customerId"
```

#### 2. Payment Processing with Retry

```yaml
process_payment:
  type: Task
  resource: "paymentService.charge"
  next: "fulfill_order"
  parameters:
    amount: "$.totalAmount"
    currency: "USD"
    paymentMethod: "$.paymentMethod"
  retry:
    maxAttempts: 3
    backoffMultiplier: 2
  timeout: 60
  catch:
    - errorType: "PaymentError"
      next: "handle_payment_failure"
```

#### 3. Inventory Check with Error Handling

```yaml
check_inventory:
  type: Task
  resource: "inventoryService.check"
  next: "inventory_decision"
  parameters:
    items: "$.items"
    warehouseId: "$.warehouseId"
  catch:
    - errorType: "InventoryError"
      next: "handle_inventory_error"
    - errorType: "*"
      next: "handle_system_error"
```

## Choice State

The Choice state provides conditional branching based on data values. It evaluates conditions and routes execution to different states.

### Basic Configuration

```yaml
choice_state:
  type: Choice
  choices:
    - condition:
        variable: "$.status"
        stringEquals: "approved"
      next: "approved_state"
  default: "default_state"
```

### Full Configuration

```yaml
inventory_decision:
  type: Choice
  choices:
    - condition:
        variable: "$.inventoryAvailable"
        booleanEquals: true
      next: "reserve_inventory"
    - condition:
        variable: "$.totalAmount"
        numericGreaterThan: 1000
      next: "high_value_process"
    - condition:
        variable: "$.customerType"
        stringEquals: "premium"
      next: "premium_process"
  default: "standard_process"
```

### Configuration Options

#### Required Fields

- **type**: Must be "Choice"
- **choices**: Array of choice conditions
- **default**: Default state if no conditions match

#### Choice Structure

Each choice has:
- **condition**: Evaluation criteria
- **next**: State to execute if condition is true

### Condition Types

#### String Comparisons

```yaml
condition:
  variable: "$.status"
  stringEquals: "approved"

condition:
  variable: "$.priority"
  stringLessThan: "high"

condition:
  variable: "$.category"
  stringGreaterThan: "electronics"
```

#### Numeric Comparisons

```yaml
condition:
  variable: "$.amount"
  numericEquals: 100

condition:
  variable: "$.quantity"
  numericLessThan: 10

condition:
  variable: "$.score"
  numericGreaterThan: 80
```

#### Boolean Comparisons

```yaml
condition:
  variable: "$.isValid"
  booleanEquals: true

condition:
  variable: "$.hasInventory"
  booleanEquals: false
```

#### Existence Checks

```yaml
condition:
  variable: "$.specialInstructions"
  isPresent: true

condition:
  variable: "$.optionalField"
  isNull: true
```

### Variable References

Use JSONPath expressions to reference data:

```yaml
# Simple field reference
variable: "$.status"

# Nested field reference
variable: "$.customer.address.city"

# Array element reference
variable: "$.items[0].productId"

# Array length check
variable: "$.items.length"
```

### Complex Conditions

**Note**: The current implementation only supports single conditions. Logical operators (AND, OR, NOT) and complex nested conditions are not yet implemented.

For complex logic, use multiple choice states or pre-computed flags:

```yaml
# Instead of: AND(amount > 100, customerType == "premium")
# Use multiple choice states:

check_amount:
  type: Choice
  choices:
    - condition:
        variable: "$.amount"
        numericGreaterThan: 100
      next: "check_customer_type"
  default: "standard_processing"

check_customer_type:
  type: Choice
  choices:
    - condition:
        variable: "$.customerType"
        stringEquals: "premium"
      next: "premium_processing"
  default: "standard_processing"
```

**Currently Supported Single Conditions:**
- String equality (`stringEquals`)
- Numeric equality (`numericEquals`)
- Numeric comparisons (`numericGreaterThan`, `numericLessThan`)
- Boolean equality (`booleanEquals`)

### Choice Execution Flow

```
Start Choice → Evaluate Conditions → Match Found? → Execute Matched State
     ↓              ↓                    ↓              ↓
  Current      Condition          Yes/No         Next State
  State       Evaluation         Decision       Execution
```

### Example Choice States

#### 1. Order Priority Routing

```yaml
order_priority_decision:
  type: Choice
  choices:
    - condition:
        variable: "$.priority"
        stringEquals: "urgent"
      next: "urgent_processing"
    - condition:
        variable: "$.amount"
        numericGreaterThan: 1000
      next: "high_value_processing"
    - condition:
        variable: "$.customerType"
        stringEquals: "vip"
      next: "vip_processing"
  default: "standard_processing"
```

#### 2. Inventory Availability Check

```yaml
inventory_decision:
  type: Choice
  choices:
    - condition:
        variable: "$.inventoryAvailable"
        booleanEquals: true
      next: "reserve_inventory"
    - condition:
        variable: "$.backorderAllowed"
        booleanEquals: true
      next: "create_backorder"
  default: "notify_unavailable"
```

#### 3. Payment Method Routing

```yaml
payment_method_decision:
  type: Choice
  choices:
    - condition:
        variable: "$.paymentMethod"
        stringEquals: "credit_card"
      next: "process_credit_card"
    - condition:
        variable: "$.paymentMethod"
        stringEquals: "paypal"
      next: "process_paypal"
    - condition:
        variable: "$.paymentMethod"
        stringEquals: "bank_transfer"
      next: "process_bank_transfer"
  default: "handle_unknown_payment"
```

## Wait State

The Wait state pauses execution for a specified duration. It's useful for implementing delays, timeouts, and scheduled operations.

### Basic Configuration

```yaml
wait_state:
  type: Wait
  seconds: 60
  next: "next_state"
```

### Configuration Options

#### Required Fields

- **type**: Must be "Wait"
- **next**: Next state to execute after waiting

#### Time Specification

Choose one of the following time formats:

**Relative Time (Seconds):**
```yaml
wait_state:
  type: Wait
  seconds: 60
  next: "next_state"
```

**Absolute Time (Timestamp):**
```yaml
wait_state:
  type: Wait
  timestamp: "2024-12-31T23:59:59Z"
  next: "next_state"
```

**ISO 8601 Format:**
```yaml
wait_state:
  type: Wait
  timestamp: "2024-01-15T10:30:00+00:00"
  next: "next_state"
```

### Wait Execution Flow

```
Start Wait → Calculate Wait Duration → Pause Execution → Resume → Next State
     ↓              ↓                    ↓              ↓        ↓
  Current      Time Calculation    Wait Period    Resume    Execute
  State       (Relative/Absolute)  (Sleep)       Execution Next State
```

### Example Wait States

#### 1. Simple Delay

```yaml
processing_delay:
  type: Wait
  seconds: 30
  next: "check_status"
```

#### 2. Scheduled Wait

```yaml
scheduled_processing:
  type: Wait
  timestamp: "2024-01-15T09:00:00Z"
  next: "start_batch_processing"
```

#### 3. Dynamic Wait

```yaml
dynamic_wait:
  type: Wait
  seconds: "$.waitDuration"
  next: "continue_processing"
```

## Success State

The Success state indicates successful workflow completion. It's a terminal state that ends the workflow execution.

### Configuration

```yaml
success_state:
  type: Success
```

### Behavior

- **Terminal State**: Workflow execution stops
- **Output**: Final workflow output is available
- **Status**: Execution status becomes "COMPLETED"
- **No Next State**: Cannot specify a next state

### Example Success States

#### 1. Order Success

```yaml
order_success:
  type: Success
```

#### 2. Payment Success

```yaml
payment_success:
  type: Success
```

#### 3. Process Completion

```yaml
process_completed:
  type: Success
```

## Fail State

The Fail state indicates workflow failure. It's a terminal state that ends the workflow execution with an error.

### Configuration

```yaml
fail_state:
  type: Fail
  error: "ErrorCode"
  cause: "Human readable error description"
```

### Configuration Options

#### Required Fields

- **type**: Must be "Fail"
- **error**: Error code for programmatic handling
- **cause**: Human-readable error description

### Behavior

- **Terminal State**: Workflow execution stops
- **Error Information**: Error code and cause are recorded
- **Status**: Execution status becomes "FAILED"
- **No Next State**: Cannot specify a next state

### Example Fail States

#### 1. Order Failure

```yaml
order_failed:
  type: Fail
  error: "OrderProcessingFailed"
  cause: "Order could not be completed due to system error"
```

#### 2. Payment Failure

```yaml
payment_failed:
  type: Fail
  error: "PaymentError"
  cause: "Payment processing failed after multiple attempts"
```

#### 3. Validation Failure

```yaml
validation_failed:
  type: Fail
  error: "ValidationError"
  cause: "Order validation failed: invalid customer information"
```

## State Combinations

### Common Patterns

#### 1. Task with Error Handling

```yaml
validate_order:
  type: Task
  resource: "orderService.validate"
  next: "check_inventory"
  retry:
    maxAttempts: 3
    backoffMultiplier: 2
  catch:
    - errorType: "ValidationError"
      next: "order_failed"
    - errorType: "*"
      next: "system_error"
```

#### 2. Choice with Multiple Paths

```yaml
routing_decision:
  type: Choice
  choices:
    - condition:
        variable: "$.priority"
        stringEquals: "high"
      next: "high_priority_process"
    - condition:
        variable: "$.amount"
        numericGreaterThan: 1000
      next: "high_value_process"
  default: "standard_process"
```

#### 3. Wait with Task

```yaml
wait_for_approval:
  type: Wait
  seconds: 3600  # 1 hour
  next: "check_approval_status"

check_approval_status:
  type: Task
  resource: "approvalService.check"
  next: "approval_decision"
```

### Best Practices

#### 1. State Naming

- Use descriptive, lowercase names
- Separate words with underscores
- Keep names under 50 characters
- Use consistent naming conventions

```yaml
# Good
validate_order
check_inventory
process_payment

# Avoid
validateOrder
checkInventory
process_payment_123
```

#### 2. Error Handling

- Always provide catch blocks for Task states
- Use specific error types when possible
- Provide meaningful error messages
- Include fallback error handlers

```yaml
catch:
  - errorType: "SpecificError"
    next: "handle_specific_error"
  - errorType: "*"
    next: "handle_generic_error"
```

#### 3. Retry Configuration

- Set reasonable maxAttempts (2-5)
- Use exponential backoff for transient failures
- Consider business impact of retries
- Test retry scenarios

```yaml
retry:
  maxAttempts: 3
  backoffMultiplier: 2
  initialDelaySeconds: 1
  maxDelaySeconds: 60
```

#### 4. Timeout Configuration

- Set timeouts based on expected task duration
- Consider external service SLAs
- Use reasonable defaults (30-300 seconds)
- Test timeout scenarios

```yaml
timeout: 60  # 1 minute for quick operations
timeout: 300 # 5 minutes for complex operations
```

## Advanced Features

### Dynamic State References

Reference state names dynamically:

```yaml
dynamic_next:
  type: Task
  resource: "routingService.getNextState"
  next: "$.nextStateName"
```

### Conditional State Execution

Use Choice states for complex routing:

```yaml
complex_routing:
  type: Choice
  choices:
    - condition:
        And:
          - variable: "$.isValid"
            booleanEquals: true
          - variable: "$.hasInventory"
            booleanEquals: true
      next: "process_order"
    - condition:
        variable: "$.isValid"
        booleanEquals: false
      next: "handle_invalid_order"
  default: "handle_no_inventory"
```

### State Chaining

Chain multiple states for complex workflows:

```yaml
workflow_chain:
  startAt: validate_input
  
  states:
    validate_input:
      type: Task
      resource: "validationService.validate"
      next: "process_data"
    
    process_data:
      type: Task
      resource: "dataService.process"
      next: "choice_decision"
    
    choice_decision:
      type: Choice
      choices:
        - condition:
            variable: "$.requiresApproval"
            booleanEquals: true
          next: "wait_for_approval"
      default: "complete_process"
    
    wait_for_approval:
      type: Wait
      seconds: 3600
      next: "check_approval"
    
    check_approval:
      type: Task
      resource: "approvalService.check"
      next: "complete_process"
    
    complete_process:
      type: Success
```

## Conclusion

Understanding state types is crucial for building effective workflows. Each state type serves a specific purpose and can be combined to create complex, robust business processes.

Key takeaways:
- **Task states** execute business logic
- **Choice states** provide conditional routing
- **Wait states** implement time-based delays
- **Success/Fail states** terminate workflows
- **Proper configuration** ensures reliable execution
- **Error handling** makes workflows robust
- **Best practices** improve maintainability

Continue to the [Error Handling & Retry](error-handling-retry.md) guide for detailed information on error handling strategies.
