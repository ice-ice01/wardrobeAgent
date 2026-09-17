ALTER TABLE tryon_confirmation ADD COLUMN selected_item_ids JSON NULL AFTER provider;
ALTER TABLE tryon_task ADD COLUMN selected_item_ids JSON NULL AFTER try_on_coverage;

ALTER TABLE tryon_task_item
    ADD COLUMN selected BIT NOT NULL DEFAULT 0 AFTER rendered,
    ADD COLUMN stage_sequence INT NULL AFTER selected,
    ADD COLUMN stage_status VARCHAR(30) NOT NULL DEFAULT 'SKIPPED' AFTER stage_sequence,
    ADD COLUMN provider_task_id VARCHAR(120) NULL AFTER stage_status,
    ADD COLUMN provider_status VARCHAR(80) NULL AFTER provider_task_id,
    ADD COLUMN input_image_url VARCHAR(500) NULL AFTER provider_status,
    ADD COLUMN result_image_url VARCHAR(500) NULL AFTER input_image_url,
    ADD COLUMN error_code VARCHAR(80) NULL AFTER result_image_url,
    ADD COLUMN error_message VARCHAR(500) NULL AFTER error_code,
    ADD COLUMN started_at DATETIME(6) NULL AFTER error_message,
    ADD COLUMN completed_at DATETIME(6) NULL AFTER started_at;

CREATE TABLE ai_evaluation_run (
    id VARCHAR(40) PRIMARY KEY,
    dataset_version VARCHAR(80) NOT NULL,
    git_commit VARCHAR(80) NOT NULL,
    run_mode VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_cases INT NOT NULL,
    passed_cases INT NOT NULL,
    failed_cases INT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    report_path VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_eval_run_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_evaluation_case_result (
    id VARCHAR(40) PRIMARY KEY,
    run_id VARCHAR(40) NOT NULL,
    case_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    duration_ms BIGINT NOT NULL,
    assertion_summary VARCHAR(1000) NOT NULL,
    output_summary TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_eval_case UNIQUE (run_id, case_id),
    INDEX idx_eval_case_run (run_id),
    CONSTRAINT fk_eval_case_run FOREIGN KEY (run_id) REFERENCES ai_evaluation_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
