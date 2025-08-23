-- Migration V2: Add enhanced workflow features to execution_steps table
-- This migration adds the new columns needed for retry, timeout, and error handling features

-- Add new columns to execution_steps table
ALTER TABLE execution_steps 
ADD COLUMN IF NOT EXISTS backoff_multiplier DOUBLE PRECISION DEFAULT 2.0,
ADD COLUMN IF NOT EXISTS initial_interval_ms BIGINT DEFAULT 1000,
ADD COLUMN IF NOT EXISTS timeout_seconds INTEGER,
ADD COLUMN IF NOT EXISTS error_type VARCHAR(255),
ADD COLUMN IF NOT EXISTS run_after_ts TIMESTAMP WITH TIME ZONE;

-- Add new columns to execution_queue table if they don't exist
ALTER TABLE execution_queue 
ADD COLUMN IF NOT EXISTS retry_count INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS max_retries INTEGER DEFAULT 3,
ADD COLUMN IF NOT EXISTS backoff_multiplier DOUBLE PRECISION DEFAULT 2.0,
ADD COLUMN IF NOT EXISTS initial_interval_ms BIGINT DEFAULT 1000,
ADD COLUMN IF NOT EXISTS run_after_ts TIMESTAMP WITH TIME ZONE;

-- Add new columns to workflow_executions table if they don't exist
ALTER TABLE workflow_executions 
ADD COLUMN IF NOT EXISTS retry_count INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS max_retries INTEGER DEFAULT 3;

-- Create index on run_after_ts for better performance on retry scheduling
CREATE INDEX IF NOT EXISTS idx_execution_steps_run_after_ts ON execution_steps(run_after_ts);
CREATE INDEX IF NOT EXISTS idx_execution_queue_run_after_ts ON execution_queue(run_after_ts);

-- Create index on retry_count for better performance on retry queries
CREATE INDEX IF NOT EXISTS idx_execution_steps_retry_count ON execution_steps(retry_count);
CREATE INDEX IF NOT EXISTS idx_execution_queue_retry_count ON execution_queue(retry_count);

-- Add comments for documentation
COMMENT ON COLUMN execution_steps.backoff_multiplier IS 'Multiplier for exponential backoff on retries';
COMMENT ON COLUMN execution_steps.initial_interval_ms IS 'Initial retry interval in milliseconds';
COMMENT ON COLUMN execution_steps.timeout_seconds IS 'Timeout for step execution in seconds';
COMMENT ON COLUMN execution_steps.error_type IS 'Type of error that occurred';
COMMENT ON COLUMN execution_steps.run_after_ts IS 'Timestamp when step should run after retry delay';
