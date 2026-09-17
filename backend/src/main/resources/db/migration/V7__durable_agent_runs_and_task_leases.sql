ALTER TABLE tryon_task
    ADD COLUMN lease_owner VARCHAR(80) NULL AFTER status_version,
    ADD COLUMN lease_until DATETIME(6) NULL AFTER lease_owner,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER lease_until,
    ADD COLUMN next_attempt_at DATETIME(6) NULL AFTER attempt_count;

CREATE INDEX idx_tryon_recovery ON tryon_task(status, next_attempt_at, lease_until);

CREATE TABLE agent_run (
    id VARCHAR(40) PRIMARY KEY,
    user_id VARCHAR(40) NOT NULL,
    conversation_id VARCHAR(40) NOT NULL,
    client_message_id VARCHAR(80) NOT NULL,
    content TEXT NOT NULL,
    locked_item_ids JSON NOT NULL,
    excluded_item_ids JSON NOT NULL,
    action VARCHAR(30) NULL,
    source_outfit_id VARCHAR(40) NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    lease_owner VARCHAR(80) NULL,
    lease_until DATETIME(6) NULL,
    next_attempt_at DATETIME(6) NULL,
    status_version BIGINT NOT NULL DEFAULT 0,
    error_code VARCHAR(80) NULL,
    error_message VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_agent_run_client UNIQUE (user_id, client_message_id),
    INDEX idx_agent_run_conversation (conversation_id, created_at),
    INDEX idx_agent_run_recovery (status, next_attempt_at, lease_until),
    CONSTRAINT fk_agent_run_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE agent_run_event (
    id VARCHAR(40) PRIMARY KEY,
    run_id VARCHAR(40) NOT NULL,
    attempt_number INT NOT NULL,
    sequence_number BIGINT NOT NULL,
    event_id VARCHAR(40) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    conversation_id VARCHAR(40) NOT NULL,
    event_data JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_agent_run_event_sequence UNIQUE (run_id, attempt_number, sequence_number),
    INDEX idx_agent_run_event_replay (run_id, attempt_number, sequence_number),
    CONSTRAINT fk_agent_run_event_run FOREIGN KEY (run_id) REFERENCES agent_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
