package com.cloudstorage.common.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record FileChangeEvent(
        UUID fileId,
        String path,
        String eventType,
        int version,
        UUID userId,
        Instant timestamp
) implements Serializable {
}
