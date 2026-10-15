-- Display configuration only. Safe to apply repeatedly; no inventory or original location changes.
CREATE TABLE IF NOT EXISTS rd_visual_document (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kind VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
    scope_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    version INT NOT NULL,
    draft_content MEDIUMTEXT NOT NULL,
    published_content MEDIUMTEXT NULL,
    published_version INT NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) NOT NULL DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0', tenant_id BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id), UNIQUE KEY uk_visual_scope (tenant_id, kind, scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS rd_visual_document_event (
    id BIGINT NOT NULL AUTO_INCREMENT, document_id BIGINT NOT NULL,
    version INT NOT NULL, action VARCHAR(16) NOT NULL, content MEDIUMTEXT NOT NULL,
    creator VARCHAR(64) NOT NULL DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) NOT NULL DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0', tenant_id BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id), UNIQUE KEY uk_visual_event (tenant_id, document_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
