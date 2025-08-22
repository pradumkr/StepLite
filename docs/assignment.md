# Distributed Workflow Engine

## Problem Statement

Build a simplified distributed workflow engine that can execute multi-step workflows with state management, similar to AWS Step Functions or Apache Airflow, but much simpler.

## Context

You're building a workflow engine for an e-commerce platform that needs to orchestrate complex business processes like order fulfillment, inventory management, and payment processing. The engine must be reliable, scalable, and maintainable.

## Requirements

### Core Functionality (Required)

Build the foundational workflow engine with these capabilities:

1. **Workflow Definition**
   - Support defining workflows in JSON or YAML format
   - Workflows consist of states/steps that execute in sequence
   - Each state has a type, name, and next state

2. **State Types** (implement at least these)
   - `Task`: Executes an action (mock the actual execution)
   - `Success`: Terminal state indicating successful completion
   - `Fail`: Terminal state indicating workflow failure

3. **Execution Engine**
   - Start workflow execution with input data
   - Execute states in sequence
   - Pass data between states
   - Persist workflow execution state

4. **Basic APIs**
   ```
   POST /workflows                      - Register a workflow definition
   POST /workflow-executions            - Start a workflow execution
   GET  /workflow-executions/{id}       - Get execution status and current state
   GET  /workflow-executions/{id}/steps/{stepId} - Get specific step status
   ```

5. **State Persistence**
   - Store workflow definitions
   - Track execution progress
   - Maintain execution history

### Enhanced Features (Recommended)

Add production-ready features:

1. **Choice State**
   - Conditional branching based on input data
   - Support simple comparisons (equals, greater than, etc.)

2. **Error Handling**
   - Retry logic with configurable attempts and backoff
   - Catch and handle task failures
   - Timeout support for tasks

3. **Enhanced APIs**
   ```
   GET  /workflows                               - List all workflows
   GET  /workflow-executions                     - List executions with filtering
   PUT  /workflow-executions/{id}/cancel         - Cancel a running execution
   ```

### Advanced Features (Optional)

If time permits, implement any of these:

1. **Wait State**
   - Pause execution for a specified duration
   - Support both relative (seconds) and absolute (timestamp) waits

2. **Parallel State**
   - Execute multiple branches concurrently
   - Wait for all branches to complete

3. **Compensation/Rollback**
   - Define compensation actions for each task
   - On failure, execute compensation in reverse order

4. **Event-Driven Progression**
   - Wait for external events to advance workflow
   - Webhook/callback support

## Example Implementation

**Note:** The workflow below is a sample that demonstrates the workflow language capabilities. Your workflow engine must be extensible and capable of executing ANY workflow definition that adheres to the workflow language specification outlined in the requirements above. Do not hard-code this example as part of your core workflow engine.

You must support executing this order processing workflow:

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

## Deliverables

1. **Implementation**
   - Workflow engine implementation
   - REST API server
   - Clear project structure

2. **Testing**
   - Unit tests for core components
   - Integration test demonstrating the example workflow
   - Document what additional tests you would add

3. **Documentation**
   - README with setup instructions
   - API documentation
   - Architecture decisions and trade-offs

4. **Deployment**
   - Docker setup for easy execution
   - docker-compose for any dependencies
   - Simple script to run the example workflow

## Constraints

- Build your own workflow engine - do not use existing workflow orchestration tools (Step Functions, Temporal, Airflow, etc.)
- You MAY use infrastructure components like databases, message queues, caching systems
- You MAY use web frameworks and common libraries

---
© 2025 Freightmate AI, Inc. All rights reserved.