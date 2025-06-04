-- Chat Tables
-- Contains tables for chat threads, tool calls, and messages
CREATE TABLE IF NOT EXISTS threads
(
    id         VARCHAR(128) PRIMARY KEY,
    user_id    VARCHAR(128)             NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_threads_user_id ON threads (user_id);

CREATE TABLE IF NOT EXISTS tool_calls
(
    id           VARCHAR(128) PRIMARY KEY,
    name         TEXT                     NOT NULL,
    arguments    TEXT                     NOT NULL,
    tool_type    VARCHAR(32)              NOT NULL,
    status       VARCHAR(32)              NULL,
    initiated_at TIMESTAMP WITH TIME ZONE NULL,
    completed_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE TABLE IF NOT EXISTS messages
(
    id           VARCHAR(128) PRIMARY KEY,
    role         VARCHAR(32)              NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    content      TEXT                     NOT NULL,
    content_type VARCHAR(32)              NOT NULL,
    tool_call_id VARCHAR(128)             NULL,
    thread_id    VARCHAR(128)             NOT NULL,
    CONSTRAINT fk_messages_tool_call FOREIGN KEY (tool_call_id) REFERENCES tool_calls (id) ON DELETE RESTRICT,
    CONSTRAINT fk_messages_thread FOREIGN KEY (thread_id) REFERENCES threads (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_messages_thread_id ON messages (thread_id);
CREATE INDEX IF NOT EXISTS idx_messages_tool_call_id ON messages (tool_call_id);
