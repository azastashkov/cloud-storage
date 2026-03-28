package com.cloudstorage.blockserver.service;

import com.cloudstorage.blockserver.entity.BlockEntity;
import com.cloudstorage.blockserver.entity.FileEntity;
import com.cloudstorage.blockserver.entity.FileVersionBlockEntity;
import com.cloudstorage.blockserver.entity.FileVersionEntity;
import com.cloudstorage.blockserver.repository.FileRepository;
import com.cloudstorage.blockserver.repository.FileVersionBlockRepository;
import com.cloudstorage.blockserver.repository.FileVersionRepository;
import com.cloudstorage.common.dto.UploadResponse;
import com.cloudstorage.common.event.FileChangeEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FileUploadService {

    private final BlockSplitterService blockSplitterService;
    private final BlockHashService blockHashService;
    private final BlockCompressionService blockCompressionService;
    private final BlockStorageService blockStorageService;
    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final FileVersionBlockRepository fileVersionBlockRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Counter fileUploadsCounter;
    private final DistributionSummary uploadSizeSummary;
    private final DistributionSummary compressionRatioSummary;

    public FileUploadService(BlockSplitterService blockSplitterService,
                             BlockHashService blockHashService,
                             BlockCompressionService blockCompressionService,
                             BlockStorageService blockStorageService,
                             FileRepository fileRepository,
                             FileVersionRepository fileVersionRepository,
                             FileVersionBlockRepository fileVersionBlockRepository,
                             RabbitTemplate rabbitTemplate,
                             MeterRegistry meterRegistry) {
        this.blockSplitterService = blockSplitterService;
        this.blockHashService = blockHashService;
        this.blockCompressionService = blockCompressionService;
        this.blockStorageService = blockStorageService;
        this.fileRepository = fileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.fileVersionBlockRepository = fileVersionBlockRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.fileUploadsCounter = Counter.builder("cloud_storage_file_uploads_total")
                .description("Total file uploads")
                .register(meterRegistry);
        this.uploadSizeSummary = DistributionSummary.builder("cloud_storage_upload_size_bytes")
                .description("Upload file sizes")
                .register(meterRegistry);
        this.compressionRatioSummary = DistributionSummary.builder("cloud_storage_compression_ratio")
                .description("Compression ratio")
                .register(meterRegistry);
    }

    @Transactional
    public UploadResponse uploadFile(InputStream fileStream, long fileSize,
                                     String filename, UUID userId) throws IOException {
        return doUpload(fileStream, fileSize, filename, userId, null);
    }

    @Transactional
    public UploadResponse uploadFileWithExpectedVersion(InputStream fileStream, long fileSize,
                                                        String filename, UUID userId,
                                                        int expectedVersion) throws IOException {
        return doUpload(fileStream, fileSize, filename, userId, expectedVersion);
    }

    private UploadResponse doUpload(InputStream fileStream, long fileSize,
                                    String filename, UUID userId,
                                    Integer expectedVersion) throws IOException {
        String filePath = "/" + filename;

        // Split file into blocks first (before acquiring lock to minimize lock duration)
        List<byte[]> blocks = blockSplitterService.split(fileStream, fileSize);

        int deduplicatedCount = 0;
        long totalOriginalSize = 0;
        long totalCompressedSize = 0;

        // Process blocks (idempotent due to deduplication, safe outside lock)
        BlockEntity[] storedBlocks = new BlockEntity[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            byte[] blockData = blocks.get(i);
            String hash = blockHashService.computeHash(blockData);
            byte[] compressed = blockCompressionService.compress(blockData);

            totalOriginalSize += blockData.length;
            totalCompressedSize += compressed.length;

            try {
                storedBlocks[i] = blockStorageService.storeBlock(hash, compressed, blockData.length);
            } catch (Exception e) {
                throw new RuntimeException("Failed to store block " + i + " for file " + filename, e);
            }

            if (storedBlocks[i].getReferenceCount() > 1) {
                deduplicatedCount++;
            }
        }

        // Acquire pessimistic lock on file entity to serialize version creation
        FileEntity fileEntity = fileRepository.findByUserIdAndFilePathForUpdate(userId, filePath)
                .orElseGet(() -> {
                    FileEntity newFile = new FileEntity();
                    newFile.setFilename(filename);
                    newFile.setFilePath(filePath);
                    newFile.setFileSize(0);
                    newFile.setUserId(userId);
                    newFile.setLatestVersion(0);
                    newFile.setStatus("ACTIVE");
                    newFile.setCreatedAt(LocalDateTime.now());
                    newFile.setUpdatedAt(LocalDateTime.now());
                    return fileRepository.save(newFile);
                });

        boolean isConflict = false;
        if (expectedVersion != null && expectedVersion != fileEntity.getLatestVersion()) {
            isConflict = true;
            log.warn("Version conflict for file {}: expected {}, actual {}",
                    filename, expectedVersion, fileEntity.getLatestVersion());
        }

        int newVersion = fileEntity.getLatestVersion() + 1;

        FileVersionEntity versionEntity = new FileVersionEntity();
        versionEntity.setFileId(fileEntity.getId());
        versionEntity.setVersionNumber(newVersion);
        versionEntity.setBlockCount(blocks.size());
        versionEntity.setTotalSize(fileSize);
        versionEntity.setCreatedBy(userId);
        versionEntity.setCreatedAt(LocalDateTime.now());
        versionEntity.setConflict(isConflict);
        versionEntity.setBaseVersion(expectedVersion);
        versionEntity = fileVersionRepository.save(versionEntity);

        // Create block-version mappings
        for (int i = 0; i < storedBlocks.length; i++) {
            FileVersionBlockEntity versionBlock = new FileVersionBlockEntity();
            versionBlock.setFileVersionId(versionEntity.getId());
            versionBlock.setBlockId(storedBlocks[i].getId());
            versionBlock.setBlockOrder(i);
            fileVersionBlockRepository.save(versionBlock);
        }

        fileEntity.setLatestVersion(newVersion);
        fileEntity.setFileSize(fileSize);
        fileRepository.save(fileEntity);

        fileUploadsCounter.increment();
        uploadSizeSummary.record(fileSize);
        if (totalOriginalSize > 0) {
            compressionRatioSummary.record((double) totalCompressedSize / totalOriginalSize);
        }

        try {
            FileChangeEvent event = new FileChangeEvent(
                    fileEntity.getId(),
                    filePath,
                    "UPLOAD",
                    newVersion,
                    userId,
                    Instant.now()
            );
            rabbitTemplate.convertAndSend("file-change-exchange", "file.change.upload", event);
        } catch (Exception e) {
            log.warn("Failed to publish file change event for {}: {}", filename, e.getMessage());
        }

        log.info("Uploaded file: {} (version {}, {} blocks, {} deduplicated)",
                filename, newVersion, blocks.size(), deduplicatedCount);

        return new UploadResponse(fileEntity.getId(), newVersion, blocks.size(), deduplicatedCount);
    }
}
