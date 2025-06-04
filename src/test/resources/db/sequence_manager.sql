-- Sequence Manager Table
-- Manages custom sequence values for generating unique IDs across the application
CREATE TABLE IF NOT EXISTS sequence_manager
(
    sequence_name VARCHAR(255) PRIMARY KEY,
    current_value BIGINT NOT NULL DEFAULT 0
);

-- Create an index on sequence_name for faster lookups
CREATE INDEX IF NOT EXISTS idx_sequence_manager_name ON sequence_manager (sequence_name);

-- Add comment to the table
COMMENT ON TABLE sequence_manager IS 'Manages custom sequence values for generating unique IDs';
COMMENT ON COLUMN sequence_manager.sequence_name IS 'Unique identifier for the sequence';
COMMENT ON COLUMN sequence_manager.current_value IS 'Current value of the sequence, incremented when a new value is requested';
