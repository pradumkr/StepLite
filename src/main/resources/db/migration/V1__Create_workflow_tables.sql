-- Create workflows table
CREATE TABLE workflows (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create workflow_versions table
CREATE TABLE workflow_versions (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    version VARCHAR(50) NOT NULL,
    definition_jsonb JSONB NOT NULL,
    is_active BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(workflow_id, version)
);

-- Create workflow_executions table
CREATE TABLE workflow_executions (
    id BIGSERIAL PRIMARY KEY,
    workflow_version_id BIGINT NOT NULL REFERENCES workflow_versions(id),
    execution_id VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'RUNNING',
    current_state VARCHAR(255),
    input_data JSONB,
    output_data JSONB,
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create execution_steps table
CREATE TABLE execution_steps (
    id BIGSERIAL PRIMARY KEY,
    execution_id BIGINT NOT NULL REFERENCES workflow_executions(id) ON DELETE CASCADE,
    step_name VARCHAR(255) NOT NULL,
    step_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    input_data JSONB,
    output_data JSONB,
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create execution_history table
CREATE TABLE execution_history (
    id BIGSERIAL PRIMARY KEY,
    execution_id BIGINT NOT NULL REFERENCES workflow_executions(id) ON DELETE CASCADE,
    step_name VARCHAR(255),
    event_type VARCHAR(50) NOT NULL,
    event_data JSONB,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create execution_queue table
CREATE TABLE execution_queue (
    id BIGSERIAL PRIMARY KEY,
    execution_id BIGINT NOT NULL REFERENCES workflow_executions(id) ON DELETE CASCADE,
    priority INTEGER DEFAULT 0,
    scheduled_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) DEFAULT 'QUEUED',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create idempotency_keys table
CREATE TABLE idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    key_hash VARCHAR(255) NOT NULL UNIQUE,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(255),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indices for better performance
CREATE INDEX idx_workflows_name ON workflows(name);
CREATE INDEX idx_workflow_versions_workflow_id ON workflow_versions(workflow_id);
CREATE INDEX idx_workflow_versions_active ON workflow_versions(is_active);
CREATE INDEX idx_workflow_executions_status ON workflow_executions(status);
CREATE INDEX idx_workflow_executions_execution_id ON workflow_executions(execution_id);
CREATE INDEX idx_execution_steps_execution_id ON execution_steps(execution_id);
CREATE INDEX idx_execution_steps_status ON execution_steps(status);
CREATE INDEX idx_execution_queue_status ON execution_queue(status);
CREATE INDEX idx_execution_queue_scheduled_at ON execution_queue(scheduled_at);
CREATE INDEX idx_execution_queue_priority ON execution_queue(priority);
CREATE INDEX idx_idempotency_keys_hash ON idempotency_keys(key_hash);
CREATE INDEX idx_idempotency_keys_expires ON idempotency_keys(expires_at);

-- Create GIN indices for JSONB columns
CREATE INDEX idx_workflow_versions_definition ON workflow_versions USING GIN (definition_jsonb);
CREATE INDEX idx_workflow_executions_input ON workflow_executions USING GIN (input_data);
CREATE INDEX idx_workflow_executions_output ON workflow_executions USING GIN (output_data);
CREATE INDEX idx_execution_steps_input ON execution_steps USING GIN (input_data);
CREATE INDEX idx_execution_steps_output ON execution_steps USING GIN (output_data);
CREATE INDEX idx_execution_history_event_data ON execution_history USING GIN (event_data);

-- Create updated_at trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at
CREATE TRIGGER update_workflows_updated_at BEFORE UPDATE ON workflows
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_workflow_versions_updated_at BEFORE UPDATE ON workflow_versions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_workflow_executions_updated_at BEFORE UPDATE ON workflow_executions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_execution_steps_updated_at BEFORE UPDATE ON execution_steps
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_execution_queue_updated_at BEFORE UPDATE ON execution_queue
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
