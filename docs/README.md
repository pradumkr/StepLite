# StepLite Documentation

Welcome to the StepLite documentation. StepLite is a lightweight, distributed workflow engine inspired by AWS Step Functions, providing a simple yet powerful way to orchestrate complex business processes.

## Documentation Index

### Core System Documentation
- [System Overview](system-overview.md) - High-level architecture and design principles
- [Workflow Language Specification](workflow-language-spec.md) - Complete workflow definition syntax
- [API Reference](api-reference.md) - All available REST endpoints with examples
- [Architecture Deep Dive](architecture-deep-dive.md) - Detailed system architecture and components

### Feature Documentation
- [Workflow Management](workflow-management.md) - How to register and manage workflows
- [Workflow Execution](workflow-execution.md) - How to execute workflows and monitor progress
- [State Types](state-types.md) - Available state types and their configurations
- [Error Handling & Retry](error-handling-retry.md) - Error handling, retry logic, and timeout support
- [Choice & Conditional Logic](choice-conditional-logic.md) - Conditional branching and decision making
- [Wait States](wait-states.md) - Pausing execution for time-based delays

### Integration & Usage
- [Getting Started](getting-started.md) - Quick start guide and setup instructions
- [Testing Guide](testing-guide.md) - How to test the system using Postman
- [Integration Guide](integration-guide.md) - How to integrate with external systems
- [Best Practices](best-practices.md) - Recommended patterns and practices

### Operational
- [Deployment](deployment.md) - Docker setup and deployment instructions
- [Configuration](configuration.md) - System configuration options
- [Monitoring & Observability](monitoring-observability.md) - Health checks, metrics, and logging
- [Troubleshooting](troubleshooting.md) - Common issues and solutions

## Quick Start

1. **Setup**: Follow the [Getting Started](getting-started.md) guide
2. **Test**: Use the [Testing Guide](testing-guide.md) to verify functionality
3. **Integrate**: Follow the [Integration Guide](integration-guide.md) to connect your systems

## System Requirements

- Java 17+
- PostgreSQL 12+
- Redis 6+
- Docker & Docker Compose (for containerized deployment)

## Support

For questions or issues, please refer to the troubleshooting guide or create an issue in the project repository.
