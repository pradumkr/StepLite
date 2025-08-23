-- Initialize database with extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create additional indexes for better performance
CREATE INDEX IF NOT EXISTS idx_workflow_executions_workflow_version_id ON workflow_executions(workflow_version_id);
CREATE INDEX IF NOT EXISTS idx_execution_steps_step_name ON execution_steps(step_name);
CREATE INDEX IF NOT EXISTS idx_execution_history_execution_id_timestamp ON execution_history(execution_id, timestamp);

-- Grant necessary permissions
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO workflow_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO workflow_user;
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO workflow_user;
