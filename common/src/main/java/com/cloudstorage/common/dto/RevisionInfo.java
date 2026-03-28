package com.cloudstorage.common.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record RevisionInfo(
        UUID versionId,
        int versionNumber,
        long fileSize,
        int blockCount,
        Instant createdAt,
        String createdBy
) implements Serializable {
}
