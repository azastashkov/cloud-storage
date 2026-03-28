package com.cloudstorage.common.dto;

import java.io.Serializable;
import java.util.UUID;

public record FileMetadataDto(
        UUID id,
        String filename,
        String path,
        long size,
        int latestVersion,
        String status
) implements Serializable {
}
