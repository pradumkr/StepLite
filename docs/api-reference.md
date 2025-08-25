# API Reference

## Base URL

```
http://localhost:8080
```

## Authentication

Currently, the API does not require authentication. In production, consider implementing:
- API Key authentication
- OAuth 2.0
- JWT tokens

## Common Headers

```
Content-Type: application/json
Accept: application/json
Idempotency-Key: <unique-key> (optional, for workflow execution)
```

## Error Responses

All endpoints return consistent error responses:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/workflows",
  "details": [
    "Workflow name is required",
    "Start state is required"
  ]
}
```

## Workflow Management APIs

### 1. Register Workflow

**Endpoint:** `POST /workflows`

**Description:** Register a new workflow definition or update an existing one.

**Request Body:**
```json
{
  "name": "order_processing_workflow",
  "version": "1.0",
  "description": "Process customer orders end-to-end",
  "startAt": "validate_order",
  "states": {
    "validate_order": {
      "type": "Task",
      "resource": "orderService.validate",
      "next": "check_inventory"
    }
  }
}
```

**Response (201 Created):**
```json
{
  "workflowVersionId": 123,
  "message": "Workflow registered successfully"
}
```

**Error Codes:**
- `400 Bad Request`: Invalid workflow definition
- `409 Conflict`: Workflow version already exists
- `500 Internal Server Error`: Server error

### 2. Get All Workflows

**Endpoint:** `GET /workflows`

**Description:** Retrieve all registered workflows.

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "name": "order_processing_workflow",
    "version": "1.0",
    "description": "Process customer orders end-to-end",
    "startAt": "validate_order",
    "states": {
      "validate_order": {
        "type": "Task",
        "resource": "orderService.validate",
        "next": "check_inventory"
      }
    },
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:00Z"
  }
]
```

### 3. Get Workflow by ID

**Endpoint:** `GET /workflows/{id}`

**Description:** Retrieve a specific workflow by its ID.

**Path Parameters:**
- `id`: Workflow ID (Long)

**Response (200 OK):**
```json
{
  "id": 1,
  "name": "order_processing_workflow",
  "version": "1.0",
  "description": "Process customer orders end-to-end",
  "startAt": "validate_order",
  "states": {
    "validate_order": {
      "type": "Task",
      "resource": "orderService.validate",
      "next": "check_inventory"
    }
  },
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

**Error Codes:**
- `404 Not Found`: Workflow not found

## Workflow Execution APIs

### 1. Start Workflow Execution

**Endpoint:** `POST /workflow-executions`

**Description:** Start a new workflow execution.

**Headers:**
- `Idempotency-Key`: Optional unique key to prevent duplicate executions

**Request Body:**
```json
{
  "workflowName": "order_processing_workflow",
  "version": "1.0",
  "input": {
    "orderId": "ORD-12345",
    "customerId": "CUST-001",
    "items": [
      {
        "productId": "PROD-001",
        "quantity": 2,
        "price": 29.99
      }
    ],
    "totalAmount": 59.98
  }
}
```

**Response (200 OK):**
```json
{
  "executionId": 456,
  "workflowName": "order_processing_workflow",
  "version": "1.0",
  "status": "RUNNING",
  "currentState": "validate_order",
  "startTime": "2024-01-15T10:30:00Z",
  "input": {
    "orderId": "ORD-12345",
    "customerId": "CUST-001",
    "items": [
      {
        "productId": "PROD-001",
        "quantity": 2,
        "price": 29.99
      }
    ],
    "totalAmount": 59.98
  }
}
```

**Error Codes:**
- `400 Bad Request`: Invalid execution request
- `404 Not Found`: Workflow not found
- `409 Conflict`: Duplicate execution (with idempotency key)
- `500 Internal Server Error`: Server error

### 2. List Workflow Executions

**Endpoint:** `GET /workflow-executions`

**Description:** List workflow executions with filtering and pagination.

**Query Parameters:**
- `statuses`: List of statuses to filter by (optional)
- `workflowName`: Filter by workflow name (optional)
- `startDate`: Filter executions started after this date (ISO format, optional)
- `endDate`: Filter executions started before this date (ISO format, optional)
- `limit`: Maximum number of results (default: 50, max: 100)
- `offset`: Number of results to skip (default: 0)
- `sortBy`: Field to sort by (default: "createdAt")
- `sortOrder`: Sort direction - "ASC" or "DESC" (default: "DESC")

**Example Request:**
```
GET /workflow-executions?statuses=RUNNING,COMPLETED&workflowName=order_processing_workflow&limit=20&offset=0
```

**Response (200 OK):**
```json
[
  {
    "executionId": 456,
    "workflowName": "order_processing_workflow",
    "version": "1.0",
    "status": "RUNNING",
    "currentState": "validate_order",
    "startTime": "2024-01-15T10:30:00Z",
    "endTime": null,
    "input": {
      "orderId": "ORD-12345"
    }
  }
]
```

### 3. Get Workflow Execution

**Endpoint:** `GET /workflow-executions/{id}`

**Description:** Get details of a specific workflow execution.

**Path Parameters:**
- `id`: Execution ID (Long)

**Response (200 OK):**
```json
{
  "executionId": 456,
  "workflowName": "order_processing_workflow",
  "version": "1.0",
  "status": "COMPLETED",
  "currentState": "order_success",
  "startTime": "2024-01-15T10:30:00Z",
  "endTime": "2024-01-15T10:35:00Z",
  "input": {
    "orderId": "ORD-12345"
  },
  "output": {
    "orderStatus": "fulfilled",
    "trackingNumber": "TRK-789"
  }
}
```

**Error Codes:**
- `404 Not Found`: Execution not found

### 4. Get Execution Step

**Endpoint:** `GET /workflow-executions/{id}/steps/{stepId}`

**Description:** Get details of a specific execution step.

**Path Parameters:**
- `id`: Execution ID (Long)
- `stepId`: Step ID (Long)

**Response (200 OK):**
```json
{
  "stepId": 789,
  "executionId": 456,
  "stateName": "validate_order",
  "stateType": "Task",
  "status": "COMPLETED",
  "startTime": "2024-01-15T10:30:00Z",
  "endTime": "2024-01-15T10:30:05Z",
  "input": {
    "orderId": "ORD-12345"
  },
  "output": {
    "validated": true,
    "validationTime": "2024-01-15T10:30:05Z"
  },
  "error": null,
  "retryCount": 0  // Note: retry logic is not yet implemented, this field is for future use
}
```

**Error Codes:**
- `404 Not Found`: Execution or step not found

### 5. Cancel Workflow Execution

**Endpoint:** `PUT /workflow-executions/{id}/cancel`

**Description:** Cancel a running workflow execution.

**Path Parameters:**
- `id`: Execution ID (Long)

**Response (200 OK):**
```json
{
  "executionId": 456,
  "workflowName": "order_processing_workflow",
  "version": "1.0",
  "status": "CANCELLED",
  "currentState": "validate_order",
  "startTime": "2024-01-15T10:30:00Z",
  "endTime": "2024-01-15T10:32:00Z",
  "input": {
    "orderId": "ORD-12345"
  }
}
```

**Error Codes:**
- `404 Not Found`: Execution not found
- `400 Bad Request`: Execution cannot be cancelled (already completed/failed)
- `500 Internal Server Error`: Server error

## Health Check APIs

### 1. Health Check

**Endpoint:** `GET /actuator/health`

**Description:** Check system health status.

**Response (200 OK):**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "6.2.0"
      }
    }
  }
}
```

### 2. Application Info

**Endpoint:** `GET /actuator/info`

**Description:** Get application information.

**Response (200 OK):**
```json
{
  "app": {
    "name": "workflow-engine",
    "version": "1.0.0",
    "description": "Distributed Workflow Engine"
  }
}
```

## Rate Limiting

The API implements rate limiting to prevent abuse:
- **Default**: 100 requests per minute per IP
- **Burst**: 200 requests per minute per IP
- **Headers**: Rate limit information included in response headers

## Pagination

List endpoints support pagination:
- **limit**: Maximum results per page (1-100)
- **offset**: Number of results to skip
- **total**: Total number of results available

## Data Formats

### Dates
All dates are in ISO 8601 format: `YYYY-MM-DDTHH:mm:ssZ`

### Numbers
- Use standard JSON number format
- Decimal numbers supported
- No size limits (within JSON specification)

### Strings
- UTF-8 encoded
- No length restrictions (within reasonable limits)

## Response Headers

```
Content-Type: application/json
X-Request-ID: <unique-request-id>
X-Rate-Limit-Limit: 100
X-Rate-Limit-Remaining: 95
X-Rate-Limit-Reset: 1642233600
```
