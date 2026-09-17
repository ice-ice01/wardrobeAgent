CREATE TABLE wardrobe_item_embedding (
    item_id VARCHAR(40) PRIMARY KEY,
    user_id VARCHAR(40) NOT NULL,
    slot VARCHAR(20) NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    index_version VARCHAR(60) NOT NULL,
    embedding_dimension INT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    metadata_json JSON NOT NULL,
    embedding MEDIUMBLOB NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_item_embedding_user_version (user_id, model_name, index_version, slot),
    CONSTRAINT fk_item_embedding_item FOREIGN KEY (item_id) REFERENCES wardrobe_item(id) ON DELETE CASCADE,
    CONSTRAINT fk_item_embedding_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
