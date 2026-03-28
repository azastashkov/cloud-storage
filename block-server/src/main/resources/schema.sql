CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename VARCHAR(255) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL REFERENCES users(id),
    latest_version INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, file_path)
);

CREATE TABLE IF NOT EXISTS file_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id UUID NOT NULL REFERENCES files(id),
    version_number INT NOT NULL,
    block_count INT NOT NULL,
    total_size BIGINT NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    is_conflict BOOLEAN NOT NULL DEFAULT FALSE,
    base_version INT,
    UNIQUE(file_id, version_number)
);

CREATE TABLE IF NOT EXISTS blocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    block_hash VARCHAR(64) NOT NULL UNIQUE,
    block_size BIGINT NOT NULL,
    compressed_size BIGINT NOT NULL,
    minio_key VARCHAR(500) NOT NULL,
    storage_tier VARCHAR(20) NOT NULL DEFAULT 'HOT',
    reference_count INT NOT NULL DEFAULT 1,
    last_accessed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS file_version_blocks (
    file_version_id UUID NOT NULL REFERENCES file_versions(id),
    block_id UUID NOT NULL REFERENCES blocks(id),
    block_order INT NOT NULL,
    PRIMARY KEY (file_version_id, block_order)
);

CREATE TABLE IF NOT EXISTS sync_conflicts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id UUID NOT NULL REFERENCES files(id),
    user_id UUID NOT NULL REFERENCES users(id),
    local_version_id UUID NOT NULL REFERENCES file_versions(id),
    remote_version_id UUID NOT NULL REFERENCES file_versions(id),
    status VARCHAR(30) NOT NULL DEFAULT 'DETECTED',
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_files_user_id ON files(user_id);
CREATE INDEX IF NOT EXISTS idx_files_file_path ON files(file_path);
CREATE INDEX IF NOT EXISTS idx_file_versions_file_id ON file_versions(file_id);
CREATE INDEX IF NOT EXISTS idx_blocks_hash ON blocks(block_hash);
CREATE INDEX IF NOT EXISTS idx_blocks_storage_tier ON blocks(storage_tier);
CREATE INDEX IF NOT EXISTS idx_blocks_last_accessed ON blocks(last_accessed_at);
CREATE INDEX IF NOT EXISTS idx_file_version_blocks_block ON file_version_blocks(block_id);
CREATE INDEX IF NOT EXISTS idx_sync_conflicts_file ON sync_conflicts(file_id);

INSERT INTO users (id, username, email) VALUES
    ('00000000-0000-0000-0000-000000000001', 'loadtest', 'loadtest@test.com')
ON CONFLICT (username) DO NOTHING;
