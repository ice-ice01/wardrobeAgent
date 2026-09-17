CREATE TABLE app_user (
    id VARCHAR(40) PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(120) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    wardrobe_revision BIGINT NOT NULL DEFAULT 0,
    enabled BIT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE media_file (
    id VARCHAR(40) PRIMARY KEY, owner_id VARCHAR(40) NOT NULL, purpose VARCHAR(30) NOT NULL,
    mime_type VARCHAR(80) NOT NULL, size_bytes BIGINT NOT NULL, storage_path VARCHAR(500) NOT NULL,
    sha256 VARCHAR(64) NOT NULL, status VARCHAR(20) NOT NULL, deleted_at DATETIME(6) NULL,
    expires_at DATETIME(6) NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    INDEX idx_media_owner (owner_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE wardrobe_item (
    id VARCHAR(40) PRIMARY KEY, user_id VARCHAR(40) NOT NULL, name VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL, slot VARCHAR(20) NOT NULL, color VARCHAR(40) NOT NULL,
    material VARCHAR(60) NOT NULL, season_tags JSON NOT NULL, scene_tags JSON NOT NULL, style_tags JSON NOT NULL,
    image_url VARCHAR(500) NOT NULL, image_source VARCHAR(20) NOT NULL, original_file_id VARCHAR(40) NULL,
    processed_file_id VARCHAR(40) NULL, image_status VARCHAR(20) NOT NULL, image_validation_message VARCHAR(300) NULL,
    warmth_level INT NULL, breathability_level INT NULL, deleted BIT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    INDEX idx_wardrobe_user (user_id, deleted, slot), CONSTRAINT fk_wardrobe_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_model (
    id VARCHAR(40) PRIMARY KEY, user_id VARCHAR(40) NOT NULL, name VARCHAR(80) NOT NULL, type VARCHAR(20) NOT NULL,
    file_id VARCHAR(40) NULL, image_url VARCHAR(500) NOT NULL, default_model BIT NOT NULL DEFAULT 0,
    authorized BIT NOT NULL DEFAULT 0, deleted BIT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    INDEX idx_model_user (user_id, deleted), CONSTRAINT fk_model_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE conversation (
    id VARCHAR(40) PRIMARY KEY, user_id VARCHAR(40) NOT NULL, title VARCHAR(120) NOT NULL,
    active BIT NOT NULL DEFAULT 1, last_message_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    INDEX idx_conversation_user (user_id, last_message_at), CONSTRAINT fk_conversation_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE message (
    id VARCHAR(40) PRIMARY KEY, user_id VARCHAR(40) NOT NULL, conversation_id VARCHAR(40) NOT NULL,
    client_message_id VARCHAR(80) NOT NULL, role VARCHAR(20) NOT NULL, content TEXT NOT NULL, status VARCHAR(20) NOT NULL,
    prompt_version VARCHAR(30) NULL, model_name VARCHAR(80) NULL, tool_version VARCHAR(30) NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_message_client UNIQUE (user_id, client_message_id, role),
    INDEX idx_message_conversation (conversation_id, created_at),
    CONSTRAINT fk_message_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE recommendation_run (
    id VARCHAR(40) PRIMARY KEY, user_id VARCHAR(40) NOT NULL, conversation_id VARCHAR(40) NOT NULL,
    client_message_id VARCHAR(80) NOT NULL, scene_snapshot TEXT NOT NULL, wardrobe_revision BIGINT NOT NULL,
    candidate_item_ids JSON NOT NULL, locked_item_ids JSON NOT NULL, excluded_item_ids JSON NOT NULL,
    shown_combination_keys JSON NOT NULL, status VARCHAR(30) NOT NULL, next_cursor VARCHAR(200) NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    INDEX idx_run_conversation (conversation_id, created_at),
    CONSTRAINT fk_run_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_run_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE outfit_plan (
    id VARCHAR(40) PRIMARY KEY, user_id VARCHAR(40) NOT NULL, conversation_id VARCHAR(40) NOT NULL,
    recommendation_run_id VARCHAR(40) NOT NULL, title VARCHAR(120) NOT NULL, scene VARCHAR(500) NOT NULL,
    plan_version INT NOT NULL, status VARCHAR(20) NOT NULL, complete BIT NOT NULL, missing_slots JSON NOT NULL,
    reason TEXT NOT NULL, notes TEXT NULL, exploration_status VARCHAR(30) NOT NULL, wardrobe_total INT NOT NULL,
    eligible_total INT NOT NULL, considered_count INT NOT NULL, has_more BIT NOT NULL, wardrobe_revision BIGINT NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    INDEX idx_plan_user (user_id, created_at), INDEX idx_plan_conversation (conversation_id, created_at),
    CONSTRAINT fk_plan_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_plan_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id),
    CONSTRAINT fk_plan_run FOREIGN KEY (recommendation_run_id) REFERENCES recommendation_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE outfit_plan_item (
    id VARCHAR(40) PRIMARY KEY, plan_id VARCHAR(40) NOT NULL, item_id VARCHAR(40) NOT NULL, slot VARCHAR(20) NOT NULL,
    item_name VARCHAR(100) NOT NULL, image_url VARCHAR(500) NOT NULL, locked BIT NOT NULL, layer_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    INDEX idx_plan_item_plan (plan_id, layer_order), CONSTRAINT fk_plan_item_plan FOREIGN KEY (plan_id) REFERENCES outfit_plan(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tryon_confirmation (
    id VARCHAR(40) PRIMARY KEY, user_id VARCHAR(40) NOT NULL, plan_id VARCHAR(40) NOT NULL, plan_version INT NOT NULL,
    user_model_id VARCHAR(40) NOT NULL, provider VARCHAR(30) NOT NULL, token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME(6) NOT NULL, consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    INDEX idx_confirmation_user (user_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tryon_task (
    id VARCHAR(40) PRIMARY KEY, user_id VARCHAR(40) NOT NULL, plan_id VARCHAR(40) NOT NULL, plan_version INT NOT NULL,
    user_model_id VARCHAR(40) NOT NULL, provider VARCHAR(30) NOT NULL, provider_task_id VARCHAR(120) NULL,
    status VARCHAR(30) NOT NULL, provider_status VARCHAR(80) NULL, idempotency_key VARCHAR(100) NOT NULL,
    retry_from_task_id VARCHAR(40) NULL, poll_count INT NOT NULL DEFAULT 0, next_poll_at DATETIME(6) NULL,
    last_polled_at DATETIME(6) NULL, deadline_at DATETIME(6) NULL, error_code VARCHAR(80) NULL,
    provider_error_code VARCHAR(80) NULL, error_message VARCHAR(500) NULL, result_image_url VARCHAR(500) NULL,
    provider_result_url VARCHAR(500) NULL, result_expires_at DATETIME(6) NULL, try_on_coverage VARCHAR(30) NOT NULL,
    rendered_item_ids JSON NOT NULL, unrendered_item_ids JSON NOT NULL, confirmation_token_hash VARCHAR(64) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL, status_version BIGINT NOT NULL DEFAULT 0, simulate_failure BIT NOT NULL DEFAULT 0,
    row_version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_tryon_idempotency UNIQUE (user_id, idempotency_key), INDEX idx_tryon_user (user_id, created_at),
    INDEX idx_tryon_status (status, next_poll_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tryon_task_item (
    id VARCHAR(40) PRIMARY KEY, task_id VARCHAR(40) NOT NULL, item_id VARCHAR(40) NOT NULL, slot VARCHAR(20) NOT NULL,
    item_name VARCHAR(100) NOT NULL, image_url VARCHAR(500) NOT NULL, rendered BIT NOT NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    INDEX idx_task_item_task (task_id), CONSTRAINT fk_task_item_task FOREIGN KEY (task_id) REFERENCES tryon_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
