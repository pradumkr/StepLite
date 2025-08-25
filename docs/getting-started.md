# Getting Started Guide

## Overview

This guide will help you get the Workflow Engine up and running quickly. It covers system setup, configuration, and running your first workflow.

## Prerequisites

### System Requirements

- **Operating System**: Linux, macOS, or Windows
- **Java**: OpenJDK 17 or later
- **Memory**: Minimum 2GB RAM, recommended 4GB+
- **Disk Space**: At least 1GB free space
- **Docker**: Docker 20.10+ and Docker Compose 2.0+ (for containerized deployment)

### Software Dependencies

- **PostgreSQL**: 12.0 or later
- **Redis**: 6.0 or later
- **Maven**: 3.6+ (for building from source)

## Quick Start with Docker

The fastest way to get started is using Docker Compose.

### 1. Clone the Repository

```bash
git clone <repository-url>
cd se-assignment-distributed-workflow-engine-pradumkumar
```

### 2. Start Services

```bash
docker-compose up -d
```

This command will start:
- PostgreSQL database
- Redis cache/queue
- Workflow Engine application

### 3. Verify Services

Check if all services are running:

```bash
docker-compose ps
```

You should see all services in "Up" status.

### 4. Test the System

Open your browser and navigate to:
- **Health Check**: http://localhost:8080/actuator/health
- **Application Info**: http://localhost:8080/actuator/info

## Manual Setup

If you prefer to run the system manually or need to customize the setup:

### 1. Database Setup

#### PostgreSQL Installation

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install postgresql postgresql-contrib
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

**macOS (using Homebrew):**
```bash
brew install postgresql
brew services start postgresql
```

**Windows:**
Download and install from [PostgreSQL official website](https://www.postgresql.org/download/windows/)

#### Create Database and User

```bash
# Connect to PostgreSQL as superuser
sudo -u postgres psql

# Create database and user
CREATE DATABASE workflow_engine;
CREATE USER workflow_user WITH PASSWORD 'workflow_pass';
GRANT ALL PRIVILEGES ON DATABASE workflow_engine TO workflow_user;
ALTER USER workflow_user CREATEDB;

# Exit PostgreSQL
\q
```

#### Initialize Schema

The application uses Flyway for database migrations. The schema will be automatically created when you start the application.

### 2. Redis Setup

#### Redis Installation

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install redis-server
sudo systemctl start redis-server
sudo systemctl enable redis-server
```

**macOS (using Homebrew):**
```bash
brew install redis
brew services start redis
```

**Windows:**
Download and install from [Redis official website](https://redis.io/download)

#### Verify Redis

```bash
redis-cli ping
```

Should return "PONG".

### 3. Application Setup

#### Build the Application

```bash
# Navigate to project directory
cd se-assignment-distributed-workflow-engine-pradumkumar

# Build with Maven
./mvnw clean package
```

#### Configure Environment Variables

Create a `.env` file in the project root:

```bash
# Database Configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=workflow_engine
DB_USER=workflow_user
DB_PASSWORD=workflow_pass

# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379

# Application Configuration
SERVER_PORT=8080
```

#### Start the Application

```bash
# Run with Maven
./mvnw spring-boot:run

# Or run the JAR file
java -jar target/workflow-engine-1.0.0.jar
```

## Configuration

### Application Configuration

The main configuration file is `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:workflow_engine}
    username: ${DB_USER:workflow_user}
    password: ${DB_PASSWORD:workflow_pass}
  
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}

workflow:
  worker:
    batch-size: 10
    stuck-step-timeout-minutes: 30

server:
  port: ${SERVER_PORT:8080}
```

### Environment-Specific Configuration

Create environment-specific configuration files:

**application-dev.yml:**
```yaml
spring:
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: create-drop

logging:
  level:
    com.freightmate.workflow: DEBUG
```

**application-prod.yml:**
```yaml
spring:
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate

logging:
  level:
    com.freightmate.workflow: INFO
```

## Your First Workflow

### 1. Create a Simple Workflow

Create a file named `hello-world-workflow.yaml`:

```yaml
name: hello_world_workflow
version: 1.0
description: A simple hello world workflow
startAt: say_hello

states:
  say_hello:
    type: Task
    resource: "greetingService.hello"
    next: success
    parameters:
      message: "Hello, World!"
  
  success:
    type: Success
```

### 2. Register the Workflow

Using curl:

```bash
curl -X POST http://localhost:8080/workflows \
  -H "Content-Type: application/json" \
  -d @hello-world-workflow.yaml
```

Or using Postman:
- Method: POST
- URL: http://localhost:8080/workflows
- Headers: Content-Type: application/json
- Body: Copy the YAML content above

### 3. Start Workflow Execution

```bash
curl -X POST http://localhost:8080/workflow-executions \
  -H "Content-Type: application/json" \
  -d '{
    "workflowName": "hello_world_workflow",
    "version": "1.0",
    "input": {
      "name": "Developer"
    }
  }'
```

### 4. Monitor Execution

Get execution status:

```bash
# Replace {executionId} with the ID from the previous response
curl http://localhost:8080/workflow-executions/{executionId}
```

## Testing the System

### 1. Health Check

```bash
curl http://localhost:8080/actuator/health
```

Expected response:
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

### 2. List Workflows

```bash
curl http://localhost:8080/workflows
```

### 3. List Executions

```bash
curl http://localhost:8080/workflow-executions
```

## Troubleshooting

### Common Issues

#### 1. Database Connection Failed

**Symptoms:**
- Application fails to start
- Error: "Failed to configure a DataSource"

**Solutions:**
- Verify PostgreSQL is running: `sudo systemctl status postgresql`
- Check database credentials in `.env` file
- Ensure database exists: `psql -U workflow_user -d workflow_engine`

#### 2. Redis Connection Failed

**Symptoms:**
- Application starts but Redis health check fails
- Error: "Redis connection failed"

**Solutions:**
- Verify Redis is running: `redis-cli ping`
- Check Redis host and port configuration
- Ensure Redis is accessible from the application

#### 3. Port Already in Use

**Symptoms:**
- Application fails to start
- Error: "Port 8080 is already in use"

**Solutions:**
- Change port in configuration: `SERVER_PORT=8081`
- Kill process using port 8080: `lsof -ti:8080 | xargs kill -9`
- Use different port in your requests

#### 4. Workflow Registration Fails

**Symptoms:**
- HTTP 400 error when registering workflow
- Validation errors

**Solutions:**
- Check workflow YAML syntax
- Ensure all required fields are present
- Verify state transitions are valid

### Debug Mode

Enable debug logging by setting the log level:

```bash
export LOGGING_LEVEL_COM_FREIGHTMATE_WORKFLOW=DEBUG
```

Or add to `application.yml`:

```yaml
logging:
  level:
    com.freightmate.workflow: DEBUG
```

### Log Files

Check application logs:

```bash
# If running with Maven
tail -f target/workflow-engine.log

# If running with Docker
docker-compose logs -f workflow-engine
```

## Next Steps

### 1. Explore the API

- Review the [API Reference](api-reference.md)
- Try different workflow patterns
- Test error scenarios

### 2. Build Complex Workflows

- Create workflows with conditional logic
- Add error handling and retries
- Implement timeouts and wait states

### 3. Integration

- Connect external services
- Implement custom task handlers
- Set up monitoring and alerting

### 4. Production Deployment

- Review [Deployment Guide](deployment.md)
- Set up production database
- Configure monitoring and logging

## Support

### Getting Help

1. **Check Logs**: Application logs often contain helpful error information
2. **Review Documentation**: This guide and other documentation files
3. **Health Checks**: Use `/actuator/health` to diagnose system issues
4. **Community**: Check project issues and discussions

### Useful Commands

```bash
# Check service status
docker-compose ps

# View logs
docker-compose logs workflow-engine

# Restart services
docker-compose restart

# Stop all services
docker-compose down

# Rebuild and start
docker-compose up --build -d
```

### Performance Tuning

For better performance:

1. **Database**: Increase connection pool size
2. **Redis**: Configure memory limits
3. **JVM**: Adjust heap size and GC settings
4. **Workers**: Configure worker thread pool size

## Conclusion

You now have a working Workflow Engine! The system is ready to execute workflows and can be extended with custom business logic. 

Continue exploring the documentation to learn about advanced features, integration patterns, and production deployment strategies.
