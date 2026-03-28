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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConflictResolutionServiceTest {

    @Mock
    private SyncConflictRepository syncConflictRepository;

    @Mock
    private FileVersionRepository fileVersionRepository;

    @Mock
    private FileVersionBlockRepository fileVersionBlockRepository;

    @Mock
    private FileRepository fileRepository;

    private ConflictResolutionService conflictResolutionService;

    @BeforeEach
    void setUp() {
        conflictResolutionService = new ConflictResolutionService(
                syncConflictRepository,
                fileVersionRepository,
                fileVersionBlockRepository,
                fileRepository,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void getConflicts_returnsOnlyDetectedStatus() {
        UUID userId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID localVersionId = UUID.randomUUID();
        UUID remoteVersionId = UUID.randomUUID();

        SyncConflictEntity conflict = new SyncConflictEntity();
        conflict.setId(UUID.randomUUID());
        conflict.setFileId(fileId);
        conflict.setUserId(userId);
        conflict.setLocalVersionId(localVersionId);
        conflict.setRemoteVersionId(remoteVersionId);
        conflict.setStatus("DETECTED");

        FileVersionEntity localVersion = new FileVersionEntity();
        localVersion.setId(localVersionId);
        localVersion.setVersionNumber(2);

        FileVersionEntity remoteVersion = new FileVersionEntity();
        remoteVersion.setId(remoteVersionId);
        remoteVersion.setVersionNumber(3);

        when(syncConflictRepository.findByUserIdAndStatus(userId, "DETECTED"))
                .thenReturn(List.of(conflict));
        when(fileVersionRepository.findById(localVersionId)).thenReturn(Optional.of(localVersion));
        when(fileVersionRepository.findById(remoteVersionId)).thenReturn(Optional.of(remoteVersion));

        List<SyncConflictDto> conflicts = conflictResolutionService.getConflicts(userId);

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).status()).isEqualTo("DETECTED");
        assertThat(conflicts.get(0).localVersion()).isEqualTo(2);
        assertThat(conflicts.get(0).remoteVersion()).isEqualTo(3);
    }

    @Test
    void resolveConflict_keepServer_marksResolved() {
        UUID conflictId = UUID.randomUUID();
        SyncConflictEntity conflict = new SyncConflictEntity();
        conflict.setId(conflictId);
        conflict.setStatus("DETECTED");

        when(syncConflictRepository.findById(conflictId)).thenReturn(Optional.of(conflict));
        when(syncConflictRepository.save(any())).thenReturn(conflict);

        conflictResolutionService.resolveConflict(conflictId, "KEEP_SERVER");

        ArgumentCaptor<SyncConflictEntity> captor = ArgumentCaptor.forClass(SyncConflictEntity.class);
        verify(syncConflictRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("RESOLVED");
        assertThat(captor.getValue().getResolvedAt()).isNotNull();
    }

    @Test
    void resolveConflict_keepLocal_createsNewVersion() {
        UUID conflictId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID localVersionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SyncConflictEntity conflict = new SyncConflictEntity();
        conflict.setId(conflictId);
        conflict.setFileId(fileId);
        conflict.setLocalVersionId(localVersionId);
        conflict.setStatus("DETECTED");

        FileVersionEntity localVersion = new FileVersionEntity();
        localVersion.setId(localVersionId);
        localVersion.setFileId(fileId);
        localVersion.setVersionNumber(2);
        localVersion.setBlockCount(2);
        localVersion.setTotalSize(2048L);
        localVersion.setCreatedBy(userId);

        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setLatestVersion(3);

        FileVersionBlockEntity block1 = new FileVersionBlockEntity();
        block1.setFileVersionId(localVersionId);
        block1.setBlockId(UUID.randomUUID());
        block1.setBlockOrder(0);

        FileVersionEntity savedVersion = new FileVersionEntity();
        savedVersion.setId(UUID.randomUUID());
        savedVersion.setFileId(fileId);
        savedVersion.setVersionNumber(4);

        when(syncConflictRepository.findById(conflictId)).thenReturn(Optional.of(conflict));
        when(fileVersionRepository.findById(localVersionId)).thenReturn(Optional.of(localVersion));
        when(fileRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(fileVersionBlockRepository.findByFileVersionIdOrderByBlockOrderAsc(localVersionId))
                .thenReturn(List.of(block1));
        when(fileVersionRepository.save(any(FileVersionEntity.class))).thenReturn(savedVersion);
        when(syncConflictRepository.save(any())).thenReturn(conflict);
        when(fileRepository.save(any())).thenReturn(file);
        when(fileVersionBlockRepository.save(any())).thenReturn(block1);

        conflictResolutionService.resolveConflict(conflictId, "KEEP_LOCAL");

        verify(fileVersionRepository).save(any(FileVersionEntity.class));
        verify(fileVersionBlockRepository).save(any(FileVersionBlockEntity.class));

        ArgumentCaptor<FileEntity> fileCaptor = ArgumentCaptor.forClass(FileEntity.class);
        verify(fileRepository).save(fileCaptor.capture());
        assertThat(fileCaptor.getValue().getLatestVersion()).isEqualTo(4);
    }
}
