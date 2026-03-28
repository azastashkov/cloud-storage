package com.cloudstorage.blockserver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "blocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BlockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "block_hash", nullable = false, unique = true)
    private String blockHash;

    @Column(name = "block_size", nullable = false)
    private long blockSize;

    @Column(name = "compressed_size", nullable = false)
    private long compressedSize;

    @Column(name = "minio_key", nullable = false)
    private String minioKey;

    @Column(name = "storage_tier", nullable = false)
    private String storageTier = "HOT";

    @Column(name = "reference_count", nullable = false)
    private int referenceCount = 1;

    @Column(name = "last_accessed_at", nullable = false)
    private LocalDateTime lastAccessedAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
