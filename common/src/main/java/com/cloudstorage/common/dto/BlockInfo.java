package com.cloudstorage.common.dto;

import java.io.Serializable;

public record BlockInfo(
        int blockOrder,
        String hash,
        long size
) implements Serializable {
}
