package com.cloudstorage.apiserver.service;

import com.cloudstorage.apiserver.entity.BlockEntity;
import com.cloudstorage.apiserver.repository.BlockRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ColdStorageService {

    private final BlockRepository blockRepository;
    private final MinioClient minioClient;
    private final Counter coldStorageMovesCounter;

    @Value("${cold-storage.threshold-days}")
    private int thresholdDays;

    @Value("${minio.block-bucket}")
    private String blockBucket;

    @Value("${minio.cold-bucket}")
    private String coldBucket;

    public ColdStorageService(BlockRepository blockRepository,
                              MinioClient minioClient,
                              MeterRegistry meterRegistry) {
        this.blockRepository = blockRepository;
        this.minioClient = minioClient;
        this.coldStorageMovesCounter = Counter.builder("cloud_storage_cold_storage_moves_total")
                .description("Total blocks moved to cold storage")
                .register(meterRegistry);
    }

    @Scheduled(fixedRate = 3600000)
    public void moveBlocksToColdStorage() {
        moveBlocksOlderThan(thresholdDays);
    }

    public void moveBlocksOlderThan(int days) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        List<BlockEntity> hotBlocks = blockRepository
                .findByStorageTierAndLastAccessedAtBefore("HOT", threshold);

        log.info("Found {} blocks eligible for cold storage migration", hotBlocks.size());

        for (BlockEntity block : hotBlocks) {
            try {
                minioClient.copyObject(CopyObjectArgs.builder()
                        .bucket(coldBucket)
                        .object(block.getMinioKey())
                        .source(CopySource.builder()
                                .bucket(blockBucket)
                                .object(block.getMinioKey())
                                .build())
                        .build());

                block.setStorageTier("COLD");
                blockRepository.save(block);
                coldStorageMovesCounter.increment();

                log.debug("Moved block {} to cold storage", block.getId());
            } catch (Exception e) {
                log.error("Failed to move block {} to cold storage", block.getId(), e);
            }
        }
    }

    public void warmUpBlock(BlockEntity block) {
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(blockBucket)
                    .object(block.getMinioKey())
                    .source(CopySource.builder()
                            .bucket(coldBucket)
                            .object(block.getMinioKey())
                            .build())
                    .build());

            block.setStorageTier("HOT");
            block.setLastAccessedAt(LocalDateTime.now());
            blockRepository.save(block);

            log.info("Warmed up block {} from cold storage", block.getId());
        } catch (Exception e) {
            log.error("Failed to warm up block {}", block.getId(), e);
            throw new RuntimeException("Failed to warm up block from cold storage", e);
        }
    }
}
