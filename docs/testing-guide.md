# Testing Guide

## Overview

This guide provides comprehensive testing instructions for the Workflow Engine using Postman. It includes test data, request examples, and step-by-step testing procedures for all API endpoints.

## Prerequisites

1. **Postman** installed and configured
2. **Workflow Engine** running locally or in Docker
3. **Database** initialized and running
4. **Redis** service running

## Environment Setup

### Postman Environment Variables

Create a new environment in Postman with these variables:

```
BASE_URL: http://localhost:8080
WORKFLOW_ID: (will be populated after workflow registration)
EXECUTION_ID: (will be populated after execution start)
STEP_ID: (will be populated after step execution)
```

### Postman Collection

Import or create a collection named "Workflow Engine API Tests" with the following structure:

```
Workflow Engine API Tests/
├── Workflow Management/
│   ├── Register Workflow
│   ├── Get All Workflows
│   └── Get Workflow by ID
├── Workflow Execution/
│   ├── Start Execution
│   ├── List Executions
│   ├── Get Execution
│   ├── Get Execution Step
│   └── Cancel Execution
└── Health Checks/
    ├── Health Check
    └── Application Info
```

## Test Data

### Sample Workflow Definitions

#### 1. Simple Order Processing Workflow

```json
{
  "name": "simple_order_workflow",
  "version": "1.0",
  "description": "Simple order processing workflow for testing",
  "startAt": "validate_order",
  "states": {
    "validate_order": {
      "type": "Task",
      "resource": "orderService.validate",
      "next": "process_payment"
    },
    "process_payment": {
      "type": "Task",
      "resource": "paymentService.charge",
      "next": "order_success"
    },
    "order_success": {
      "type": "Success"
    }
  }
}
```

#### 2. Complex Order Processing Workflow

```json
{
  "name": "complex_order_workflow",
  "version": "1.0",
  "description": "Complex order processing with error handling and retries",
  "startAt": "validate_order",
  "states": {
    "validate_order": {
      "type": "Task",
      "resource": "orderService.validate",
      "next": "check_inventory",
      // Note: retry configuration is not yet implemented in the workflow engine
      "retry": {
        "maxAttempts": 3,
        "backoffMultiplier": 2
      },
      "catch": [
        {
          "errorType": "ValidationError",
          "next": "order_failed"
        }
      ]
    },
    "check_inventory": {
      "type": "Task",
      "resource": "inventoryService.check",
      "next": "inventory_decision"
    },
    "inventory_decision": {
      "type": "Choice",
      "choices": [
        {
          "condition": {
            "variable": "$.inventoryAvailable",
            "booleanEquals": true
          },
          "next": "reserve_inventory"
        }
      ],
      "default": "notify_backorder"
    },
    "reserve_inventory": {
      "type": "Task",
      "resource": "inventoryService.reserve",
      "next": "process_payment",
      "timeout": 30,
      // Note: retry configuration is not yet implemented in the workflow engine
      "retry": {
        "maxAttempts": 2
      }
    },
    "process_payment": {
      "type": "Task",
      "resource": "paymentService.charge",
      "next": "fulfill_order",
      // Note: retry configuration is not yet implemented in the workflow engine
      "retry": {
        "maxAttempts": 2
      },
      "catch": [
        {
          "errorType": "PaymentError",
          "next": "payment_failed"
        }
      ]
    },
    "fulfill_order": {
      "type": "Task",
      "resource": "fulfillmentService.ship",
      "next": "order_success"
    },
    "notify_backorder": {
      "type": "Task",
      "resource": "notificationService.backorder",
      "next": "order_failed"
    },
    "payment_failed": {
      "type": "Task",
      "resource": "paymentService.reverse",
      "next": "order_failed"
    },
    "order_success": {
      "type": "Success"
    },
    "order_failed": {
      "type": "Fail",
      "error": "OrderProcessingFailed",
      "cause": "Order could not be completed"
    }
  }
}
```

#### 3. Wait State Test Workflow

```json
{
  "name": "wait_test_workflow",
  "version": "1.0",
  "description": "Workflow to test wait states",
  "startAt": "start_process",
  "states": {
    "start_process": {
      "type": "Task",
      "resource": "testService.start",
      "next": "wait_state"
    },
    "wait_state": {
      "type": "Wait",
      "seconds": 10,
      "next": "complete_process"
    },
    "complete_process": {
      "type": "Task",
      "resource": "testService.complete",
      "next": "success"
    },
    "success": {
      "type": "Success"
    }
  }
}
```

### Sample Execution Input Data

#### 1. Order Data

```json
{
  "orderId": "ORD-TEST-001",
  "customerId": "CUST-TEST-001",
  "customerName": "John Doe",
  "customerEmail": "john.doe@example.com",
  "items": [
    {
      "productId": "PROD-001",
      "productName": "Test Product 1",
      "quantity": 2,
      "price": 29.99,
      "totalPrice": 59.98
    },
    {
      "productId": "PROD-002",
      "productName": "Test Product 2",
      "quantity": 1,
      "price": 49.99,
      "totalPrice": 49.99
    }
  ],
  "totalAmount": 109.97,
  "shippingAddress": {
    "street": "123 Test Street",
    "city": "Test City",
    "state": "TS",
    "zipCode": "12345",
    "country": "US"
  },
  "paymentMethod": "credit_card",
  "priority": "standard"
}
```

#### 2. Inventory Test Data

```json
{
  "orderId": "ORD-TEST-002",
  "customerId": "CUST-TEST-002",
  "items": [
    {
      "productId": "PROD-003",
      "quantity": 5,
      "price": 19.99
    }
  ],
  "totalAmount": 99.95,
  "inventoryAvailable": true,
  "warehouseId": "WH-001"
}
```

## Step-by-Step Testing

### Phase 1: Health Checks

#### 1. Health Check

**Request:**
```
GET {{BASE_URL}}/actuator/health
```

**Expected Response:**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    },
    "redis": {
      "status": "UP"
    }
  }
}
```

**Test Steps:**
1. Send request
2. Verify status is "UP"
3. Verify database and Redis are healthy
4. Check response time < 1 second

#### 2. Application Info

**Request:**
```
GET {{BASE_URL}}/actuator/info
```

**Expected Response:**
```json
{
  "app": {
    "name": "workflow-engine",
    "version": "1.0.0"
  }
}
```

### Phase 2: Workflow Management

#### 1. Register Simple Workflow

**Request:**
```
POST {{BASE_URL}}/workflows
Content-Type: application/json

{
  "name": "simple_order_workflow",
  "version": "1.0",
  "description": "Simple order processing workflow for testing",
  "startAt": "validate_order",
  "states": {
    "validate_order": {
      "type": "Task",
      "resource": "orderService.validate",
      "next": "process_payment"
    },
    "process_payment": {
      "type": "Task",
      "resource": "paymentService.charge",
      "next": "order_success"
    },
    "order_success": {
      "type": "Success"
    }
  }
}
```

**Expected Response:**
```json
{
  "workflowVersionId": 1,
  "message": "Workflow registered successfully"
}
```

**Test Steps:**
1. Send request
2. Verify 201 status code
3. Extract workflowVersionId
4. Set WORKFLOW_ID variable

#### 2. Register Complex Workflow

**Request:**
```
POST {{BASE_URL}}/workflows
Content-Type: application/json

[Use complex_order_workflow JSON from above]
```

**Test Steps:**
1. Send request
2. Verify 201 status code
3. Verify workflow is registered

#### 3. Get All Workflows

**Request:**
```
GET {{BASE_URL}}/workflows
```

**Expected Response:**
```json
[
  {
    "id": 1,
    "name": "simple_order_workflow",
    "version": "1.0",
    "status": "ACTIVE"
  },
  {
    "id": 2,
    "name": "complex_order_workflow",
    "version": "1.0",
    "status": "ACTIVE"
  }
]
```

**Test Steps:**
1. Send request
2. Verify 200 status code
3. Verify both workflows are returned
4. Verify workflow details are correct

#### 4. Get Workflow by ID

**Request:**
```
GET {{BASE_URL}}/workflows/{{WORKFLOW_ID}}
```

**Test Steps:**
1. Send request
2. Verify 200 status code
3. Verify workflow details match registration
4. Test with invalid ID (should return 404)

### Phase 3: Workflow Execution

#### 1. Start Simple Workflow Execution

**Request:**
```
POST {{BASE_URL}}/workflow-executions
Content-Type: application/json
Idempotency-Key: test-exec-001

{
  "workflowName": "simple_order_workflow",
  "version": "1.0",
  "input": {
    "orderId": "ORD-TEST-001",
    "customerId": "CUST-TEST-001",
    "totalAmount": 109.97
  }
}
```

**Expected Response:**
```json
{
  "executionId": 1,
  "workflowName": "simple_order_workflow",
  "version": "1.0",
  "status": "RUNNING",
  "currentState": "validate_order",
  "startTime": "2024-01-15T10:30:00Z"
}
```

**Test Steps:**
1. Send request
2. Verify 200 status code
3. Extract executionId
4. Set EXECUTION_ID variable
5. Verify status is "RUNNING"

#### 2. Start Complex Workflow Execution

**Request:**
```
POST {{BASE_URL}}/workflow-executions
Content-Type: application/json
Idempotency-Key: test-exec-002

{
  "workflowName": "complex_order_workflow",
  "version": "1.0",
  "input": {
    "orderId": "ORD-TEST-002",
    "customerId": "CUST-TEST-002",
    "items": [
      {
        "productId": "PROD-003",
        "quantity": 5,
        "price": 19.99
      }
    ],
    "totalAmount": 99.95,
    "inventoryAvailable": true
  }
}
```

**Test Steps:**
1. Send request
2. Verify 200 status code
3. Extract executionId
4. Verify status is "RUNNING"

#### 3. Test Idempotency

**Request:**
```
POST {{BASE_URL}}/workflow-executions
Content-Type: application/json
Idempotency-Key: test-exec-001

[Same body as previous request]
```

**Expected Response:**
```json
{
  "executionId": 1,
  "workflowName": "simple_order_workflow",
  "version": "1.0",
  "status": "RUNNING",
  "currentState": "validate_order"
}
```

**Test Steps:**
1. Send request with same idempotency key
2. Verify 200 status code
3. Verify same executionId is returned
4. Verify no duplicate execution is created

#### 4. List Workflow Executions

**Request:**
```
GET {{BASE_URL}}/workflow-executions?limit=10&offset=0
```

**Expected Response:**
```json
[
  {
    "executionId": 1,
    "workflowName": "simple_order_workflow",
    "status": "RUNNING",
    "currentState": "validate_order"
  },
  {
    "executionId": 2,
    "workflowName": "complex_order_workflow",
    "status": "RUNNING",
    "currentState": "validate_order"
  }
]
```

**Test Steps:**
1. Send request
2. Verify 200 status code
3. Verify both executions are returned
4. Test filtering by status: `?statuses=RUNNING`
5. Test filtering by workflow: `?workflowName=simple_order_workflow`

#### 5. Get Workflow Execution

**Request:**
```
GET {{BASE_URL}}/workflow-executions/{{EXECUTION_ID}}
```

**Test Steps:**
1. Send request
2. Verify 200 status code
3. Verify execution details are correct
4. Test with invalid ID (should return 404)

#### 6. Get Execution Step

**Request:**
```
GET {{BASE_URL}}/workflow-executions/{{EXECUTION_ID}}/steps/1
```

**Expected Response:**
```json
{
  "stepId": 1,
  "executionId": 1,
  "stateName": "validate_order",
  "stateType": "Task",
  "status": "COMPLETED",
  "startTime": "2024-01-15T10:30:00Z",
  "endTime": "2024-01-15T10:30:05Z"
}
```

**Test Steps:**
1. Send request
2. Verify 200 status code
3. Verify step details are correct
4. Test with invalid step ID (should return 404)

#### 7. Cancel Workflow Execution

**Request:**
```
PUT {{BASE_URL}}/workflow-executions/{{EXECUTION_ID}}/cancel
```

**Expected Response:**
```json
{
  "executionId": 1,
  "status": "CANCELLED",
  "endTime": "2024-01-15T10:32:00Z"
}
```

**Test Steps:**
1. Send request
2. Verify 200 status code
3. Verify status is "CANCELLED"
4. Test cancelling completed execution (should return 400)

### Phase 4: Error Testing

#### 1. Invalid Workflow Definition

**Request:**
```
POST {{BASE_URL}}/workflows
Content-Type: application/json

{
  "name": "invalid_workflow",
  "version": "1.0"
}
```

**Expected Response:**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": [
    "Start state is required",
    "Workflow states are required"
  ]
}
```

**Test Steps:**
1. Send request
2. Verify 400 status code
3. Verify error details are correct

#### 2. Non-existent Workflow Execution

**Request:**
```
POST {{BASE_URL}}/workflow-executions
Content-Type: application/json

{
  "workflowName": "non_existent_workflow",
  "version": "1.0",
  "input": {}
}
```

**Expected Response:**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Workflow not found: non_existent_workflow"
}
```

**Test Steps:**
1. Send request
2. Verify 404 status code
3. Verify error message is correct

#### 3. Invalid Execution Input

**Request:**
```
POST {{BASE_URL}}/workflow-executions
Content-Type: application/json

{
  "workflowName": "simple_order_workflow",
  "version": "1.0"
}
```

**Expected Response:**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Input data is required"
}
```

## Performance Testing

### Load Testing

Use Postman's Runner or Newman to test:

1. **Concurrent Workflow Registration**: 10 concurrent requests
2. **Concurrent Execution Start**: 20 concurrent requests
3. **Mixed Load**: 50 concurrent mixed operations

### Expected Performance

- **API Response Time**: < 500ms for simple operations
- **Throughput**: 100+ requests per second
- **Concurrent Executions**: 50+ simultaneous workflows

## Monitoring During Testing

### Database Queries

Monitor PostgreSQL for:
- Connection pool usage
- Query performance
- Lock contention

### Redis Metrics

Monitor Redis for:
- Memory usage
- Connection count
- Queue depth

### Application Metrics

Monitor application for:
- JVM memory usage
- Thread pool utilization
- GC activity

## Test Data Cleanup

After testing, clean up test data:

```sql
-- Clean up test workflows
DELETE FROM workflow_versions WHERE name LIKE '%test%';
DELETE FROM workflows WHERE name LIKE '%test%';

-- Clean up test executions
DELETE FROM execution_steps WHERE execution_id IN (
  SELECT id FROM workflow_executions WHERE workflow_name LIKE '%test%'
);
DELETE FROM workflow_executions WHERE workflow_name LIKE '%test%';
```

## Troubleshooting

### Common Issues

1. **Connection Refused**: Check if services are running
2. **Database Connection**: Verify PostgreSQL is accessible
3. **Redis Connection**: Verify Redis is running
4. **Workflow Stuck**: Check worker service logs
5. **Timeout Errors**: Verify task timeout configurations

### Debug Steps

1. Check application logs for errors
2. Verify database connectivity
3. Check Redis connection
4. Monitor worker service status
5. Verify workflow definitions are valid

## Test Report Template

After completing tests, document:

- **Test Environment**: OS, Java version, database version
- **Test Results**: Pass/fail for each test case
- **Performance Metrics**: Response times, throughput
- **Issues Found**: Bugs, performance problems
- **Recommendations**: Improvements, optimizations
