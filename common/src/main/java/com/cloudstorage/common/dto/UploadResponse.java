package com.cloudstorage.common.dto;

import java.io.Serializable;
import java.util.UUID;

public record UploadResponse(
        UUID fileId,
        int version,
        int blockCount,
        int deduplicatedBlocks
) implements Serializable {
}
