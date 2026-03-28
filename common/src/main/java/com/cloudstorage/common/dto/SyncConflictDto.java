package com.cloudstorage.common.dto;

import java.io.Serializable;
import java.util.UUID;

public record SyncConflictDto(
        UUID id,
        UUID fileId,
        int localVersion,
        int remoteVersion,
        String status
) implements Serializable {
}
