package com.cloudstorage.blockserver.service;

import com.cloudstorage.blockserver.entity.BlockEntity;
import com.cloudstorage.blockserver.repository.BlockRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class BlockStorageService {

    private final MinioClient minioClient;
    private final BlockRepository blockRepository;
    private final Counter blocksProcessedCounter;
    private final Counter blocksDedupCounter;
    private final String blockBucket;

    public BlockStorageService(MinioClient minioClient,
                               BlockRepository blockRepository,
                               MeterRegistry meterRegistry,
                               @Value("${minio.block-bucket}") String blockBucket) {
        this.minioClient = minioClient;
        this.blockRepository = blockRepository;
        this.blockBucket = blockBucket;
        this.blocksProcessedCounter = Counter.builder("cloud_storage_blocks_processed_total")
                .description("Total blocks processed")
                .register(meterRegistry);
        this.blocksDedupCounter = Counter.builder("cloud_storage_blocks_deduplicated_total")
                .description("Total blocks deduplicated")
                .register(meterRegistry);
    }

    public BlockEntity storeBlock(String hash, byte[] compressedData, long originalSize) {
        blocksProcessedCounter.increment();

        Optional<BlockEntity> existing = blockRepository.findByBlockHash(hash);
        if (existing.isPresent()) {
            BlockEntity block = existing.get();
            block.setReferenceCount(block.getReferenceCount() + 1);
            blocksDedupCounter.increment();
            log.debug("Deduplicated block: {}", hash);
            return blockRepository.save(block);
        }

        String minioKey = "blocks/" + hash.substring(0, 2) + "/" + hash;

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(blockBucket)
                            .object(minioKey)
                            .stream(new ByteArrayInputStream(compressedData),
                                    compressedData.length, -1)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload block to MinIO: " + hash, e);
        }

        BlockEntity block = new BlockEntity();
        block.setBlockHash(hash);
        block.setBlockSize(originalSize);
        block.setCompressedSize(compressedData.length);
        block.setMinioKey(minioKey);
        block.setStorageTier("HOT");
        block.setReferenceCount(1);
        block.setLastAccessedAt(LocalDateTime.now());
        block.setCreatedAt(LocalDateTime.now());

        try {
            return blockRepository.save(block);
        } catch (DataIntegrityViolationException e) {
            log.info("Race condition on block save, fetching existing: {}", hash);
            BlockEntity racedBlock = blockRepository.findByBlockHash(hash)
                    .orElseThrow(() -> new RuntimeException(
                            "Block not found after integrity violation: " + hash));
            racedBlock.setReferenceCount(racedBlock.getReferenceCount() + 1);
            blocksDedupCounter.increment();
            return blockRepository.save(racedBlock);
        }
    }

    public byte[] retrieveBlock(String blockHash) {
        BlockEntity block = blockRepository.findByBlockHash(blockHash)
                .orElseThrow(() -> new RuntimeException("Block not found: " + blockHash));

        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(blockBucket)
                            .object(block.getMinioKey())
                            .build()
            ).readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve block from MinIO: " + blockHash, e);
        }
    }
}
