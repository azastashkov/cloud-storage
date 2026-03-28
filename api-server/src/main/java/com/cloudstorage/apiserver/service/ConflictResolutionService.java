package com.cloudstorage.apiserver.service;

import com.cloudstorage.apiserver.entity.FileEntity;
import com.cloudstorage.apiserver.entity.FileVersionBlockEntity;
import com.cloudstorage.apiserver.entity.FileVersionEntity;
import com.cloudstorage.apiserver.entity.SyncConflictEntity;
import com.cloudstorage.apiserver.repository.FileRepository;
import com.cloudstorage.apiserver.repository.FileVersionBlockRepository;
import com.cloudstorage.apiserver.repository.FileVersionRepository;
import com.cloudstorage.apiserver.repository.SyncConflictRepository;
import com.cloudstorage.common.dto.SyncConflictDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ConflictResolutionService {

    private final SyncConflictRepository syncConflictRepository;
    private final FileVersionRepository fileVersionRepository;
    private final FileVersionBlockRepository fileVersionBlockRepository;
    private final FileRepository fileRepository;
    private final Counter conflictsCounter;

    public ConflictResolutionService(SyncConflictRepository syncConflictRepository,
                                     FileVersionRepository fileVersionRepository,
                                     FileVersionBlockRepository fileVersionBlockRepository,
                                     FileRepository fileRepository,
                                     MeterRegistry meterRegistry) {
        this.syncConflictRepository = syncConflictRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.fileVersionBlockRepository = fileVersionBlockRepository;
        this.fileRepository = fileRepository;
        this.conflictsCounter = Counter.builder("cloud_storage_sync_conflicts_total")
                .description("Total sync conflicts")
                .register(meterRegistry);
    }

    public List<SyncConflictDto> getConflicts(UUID userId) {
        List<SyncConflictEntity> conflicts = syncConflictRepository
                .findByUserIdAndStatus(userId, "DETECTED");

        return conflicts.stream()
                .map(c -> {
                    int localVersion = fileVersionRepository.findById(c.getLocalVersionId())
                            .map(FileVersionEntity::getVersionNumber)
                            .orElse(0);
                    int remoteVersion = fileVersionRepository.findById(c.getRemoteVersionId())
                            .map(FileVersionEntity::getVersionNumber)
                            .orElse(0);
                    return new SyncConflictDto(
                            c.getId(),
                            c.getFileId(),
                            localVersion,
                            remoteVersion,
                            c.getStatus()
                    );
                })
                .toList();
    }

    @Transactional
    public void resolveConflict(UUID conflictId, String resolution) {
        SyncConflictEntity conflict = syncConflictRepository.findById(conflictId)
                .orElseThrow(() -> new RuntimeException("Conflict not found: " + conflictId));

        switch (resolution) {
            case "KEEP_SERVER" -> resolveKeepServer(conflict);
            case "KEEP_LOCAL" -> resolveKeepLocal(conflict);
            case "KEEP_BOTH" -> resolveKeepBoth(conflict);
            default -> throw new IllegalArgumentException("Invalid resolution: " + resolution);
        }

        conflictsCounter.increment();
    }

    private void resolveKeepServer(SyncConflictEntity conflict) {
        conflict.setStatus("RESOLVED");
        conflict.setResolvedAt(LocalDateTime.now());
        syncConflictRepository.save(conflict);
    }

    private void resolveKeepLocal(SyncConflictEntity conflict) {
        FileVersionEntity localVersion = fileVersionRepository.findById(conflict.getLocalVersionId())
                .orElseThrow(() -> new RuntimeException("Local version not found"));

        FileEntity file = fileRepository.findById(conflict.getFileId())
                .orElseThrow(() -> new RuntimeException("File not found"));

        int newVersionNumber = file.getLatestVersion() + 1;

        FileVersionEntity newVersion = new FileVersionEntity();
        newVersion.setFileId(file.getId());
        newVersion.setVersionNumber(newVersionNumber);
        newVersion.setBlockCount(localVersion.getBlockCount());
        newVersion.setTotalSize(localVersion.getTotalSize());
        newVersion.setCreatedBy(localVersion.getCreatedBy());
        newVersion.setConflict(false);
        newVersion.setBaseVersion(localVersion.getVersionNumber());
        newVersion = fileVersionRepository.save(newVersion);

        List<FileVersionBlockEntity> localBlocks = fileVersionBlockRepository
                .findByFileVersionIdOrderByBlockOrderAsc(localVersion.getId());
        for (FileVersionBlockEntity block : localBlocks) {
            FileVersionBlockEntity newBlock = new FileVersionBlockEntity();
            newBlock.setFileVersionId(newVersion.getId());
            newBlock.setBlockId(block.getBlockId());
            newBlock.setBlockOrder(block.getBlockOrder());
            fileVersionBlockRepository.save(newBlock);
        }

        file.setLatestVersion(newVersionNumber);
        file.setFileSize(localVersion.getTotalSize());
        fileRepository.save(file);

        conflict.setStatus("RESOLVED");
        conflict.setResolvedAt(LocalDateTime.now());
        syncConflictRepository.save(conflict);
    }

    private void resolveKeepBoth(SyncConflictEntity conflict) {
        conflict.setStatus("RESOLVED");
        conflict.setResolvedAt(LocalDateTime.now());
        syncConflictRepository.save(conflict);
    }
}
