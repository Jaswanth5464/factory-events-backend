-- Drop existing table if recreating
DROP TABLE IF EXISTS events CASCADE;

-- Main events table
CREATE TABLE events (
    event_id VARCHAR(100) PRIMARY KEY,
    event_time TIMESTAMP NOT NULL,
    received_time TIMESTAMP NOT NULL,
    machine_id VARCHAR(100) NOT NULL,
    line_id VARCHAR(100),
    factory_id VARCHAR(100),
    duration_ms INTEGER NOT NULL,
    defect_count INTEGER NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for fast queries by machine and time range
CREATE INDEX idx_machine_time ON events(machine_id, event_time);

-- Index for top defect lines query
CREATE INDEX idx_factory_line_time ON events(factory_id, line_id, event_time);

-- Index for general time-based queries
CREATE INDEX idx_event_time ON events(event_time);