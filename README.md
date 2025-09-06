# StepLite - Lightweight Distributed Workflow Engine

StepLite is a lightweight, distributed workflow engine inspired by AWS Step Functions. It provides a simple yet powerful way to orchestrate complex business processes with support for various state types, error handling, and distributed execution.

## Features

- **State Types**: Support for Task, Choice, Wait, Parallel, and Pass states
- **Error Handling**: Built-in retry mechanisms and error recovery
- **Distributed Execution**: Queue-based execution with Redis for scalability
- **Monitoring**: OpenTelemetry integration with Prometheus metrics
- **RESTful API**: Easy-to-use REST endpoints for workflow management
- **Database Support**: PostgreSQL with Flyway migrations
- **Docker Ready**: Complete Docker setup for easy deployment

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.6+
- PostgreSQL 12+
- Redis 6+

### Running with Docker Compose

```bash
# Clone the repository
git clone https://github.com/pradumkr/StepLite.git
cd StepLite

# Start all services
docker-compose up -d

# The application will be available at http://localhost:8080
```

### Manual Setup

1. **Start PostgreSQL and Redis**
   ```bash
   # PostgreSQL
   createdb steplite
   
   # Redis
   redis-server
   ```

2. **Configure Environment Variables**
   ```bash
   export DB_HOST=localhost
   export DB_PORT=5432
   export DB_NAME=steplite
   export DB_USER=steplite_user
   export DB_PASSWORD=steplite_pass
   export REDIS_HOST=localhost
   export REDIS_PORT=6379
   ```

3. **Run the Application**
   ```bash
   mvn spring-boot:run
   ```

## API Documentation

### Workflow Management

- `POST /workflows` - Register a new workflow
- `GET /workflows` - List all workflows
- `GET /workflows/{id}` - Get workflow details

### Workflow Execution

- `POST /executions` - Start a new workflow execution
- `GET /executions` - List executions with filtering
- `GET /executions/{id}` - Get execution details
- `POST /executions/{id}/resume` - Resume a paused execution

## Workflow Definition

StepLite uses YAML format for workflow definitions, similar to AWS Step Functions:

```yaml
name: "Order Processing Workflow"
version: "1.0"
description: "Process customer orders with validation and fulfillment"

states:
  - name: "ValidateOrder"
    type: "Task"
    resource: "validateOrderTask"
    retry:
      - errorEquals: ["ValidationError"]
        intervalSeconds: 5
        maxAttempts: 3
    catch:
      - errorEquals: ["ValidationError"]
        next: "HandleValidationError"
    next: "CheckInventory"

  - name: "CheckInventory"
    type: "Task"
    resource: "checkInventoryTask"
    next: "ProcessPayment"

  - name: "ProcessPayment"
    type: "Choice"
    choices:
      - variable: "$.paymentMethod"
        stringEquals: "credit_card"
        next: "ProcessCreditCard"
      - variable: "$.paymentMethod"
        stringEquals: "paypal"
        next: "ProcessPayPal"
    default: "ProcessCash"

  - name: "ProcessCreditCard"
    type: "Task"
    resource: "processCreditCardTask"
    next: "FulfillOrder"

  - name: "FulfillOrder"
    type: "Task"
    resource: "fulfillOrderTask"
    end: true
```

## Architecture

StepLite follows a microservices architecture with the following components:

- **API Layer**: REST controllers for workflow management
- **Service Layer**: Business logic for workflow processing
- **Worker Service**: Background processing of workflow steps
- **Queue System**: Redis-based message queue for distributed execution
- **Database**: PostgreSQL for persistent storage
- **Monitoring**: OpenTelemetry and Prometheus for observability

## Development

### Building from Source

```bash
# Clone the repository
git clone https://github.com/pradumkr/StepLite.git
cd StepLite

# Build the project
mvn clean package

# Run tests
mvn test

# Run integration tests
mvn verify
```

### Project Structure

```
src/
├── main/
│   ├── java/com/thesmartway/steplite/
│   │   ├── controller/     # REST controllers
│   │   ├── dto/           # Data transfer objects
│   │   ├── entity/        # JPA entities
│   │   ├── repository/    # Data access layer
│   │   ├── service/       # Business logic
│   │   ├── task/          # Task handlers
│   │   └── WorkflowEngineApplication.java
│   └── resources/
│       ├── application.yml
│       └── db/migration/  # Flyway migrations
└── test/
    ├── java/com/thesmartway/steplite/
    └── resources/
```

## Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For questions, issues, or feature requests, please:

1. Check the [documentation](docs/)
2. Search existing [issues](https://github.com/pradumkr/StepLite/issues)
3. Create a new issue if needed

## Roadmap

- [ ] Web UI for workflow visualization
- [ ] Additional state types (Map, Fail, Succeed)
- [ ] Workflow versioning and rollback
- [ ] Advanced monitoring and alerting
- [ ] Kubernetes operator
- [ ] Workflow templates and marketplace

---

**StepLite** - Making workflow orchestration simple and powerful.