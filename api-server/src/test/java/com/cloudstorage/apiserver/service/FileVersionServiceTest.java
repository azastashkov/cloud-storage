package com.cloudstorage.apiserver.service;

import com.cloudstorage.apiserver.entity.FileEntity;
import com.cloudstorage.apiserver.entity.FileVersionEntity;
import com.cloudstorage.apiserver.entity.UserEntity;
import com.cloudstorage.apiserver.repository.FileRepository;
import com.cloudstorage.apiserver.repository.FileVersionRepository;
import com.cloudstorage.apiserver.repository.UserRepository;
import com.cloudstorage.common.dto.RevisionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileVersionServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileVersionRepository fileVersionRepository;

    @Mock
    private UserRepository userRepository;

    private FileVersionService fileVersionService;

    @BeforeEach
    void setUp() {
        fileVersionService = new FileVersionService(fileRepository, fileVersionRepository, userRepository);
    }

    @Test
    void getRevisions_returnsCorrectListOrderedByVersionDesc() {
        UUID fileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setFilePath("/docs/test.txt");

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");

        FileVersionEntity v2 = new FileVersionEntity();
        v2.setId(UUID.randomUUID());
        v2.setFileId(fileId);
        v2.setVersionNumber(2);
        v2.setTotalSize(2048L);
        v2.setBlockCount(2);
        v2.setCreatedBy(userId);
        v2.setCreatedAt(LocalDateTime.of(2025, 1, 2, 0, 0));

        FileVersionEntity v1 = new FileVersionEntity();
        v1.setId(UUID.randomUUID());
        v1.setFileId(fileId);
        v1.setVersionNumber(1);
        v1.setTotalSize(1024L);
        v1.setBlockCount(1);
        v1.setCreatedBy(userId);
        v1.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));

        when(fileRepository.findByFilePath("/docs/test.txt")).thenReturn(Optional.of(file));
        when(fileVersionRepository.findByFileIdOrderByVersionNumberDesc(fileId))
                .thenReturn(List.of(v2, v1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        List<RevisionInfo> revisions = fileVersionService.getRevisions("/docs/test.txt", 10);

        assertThat(revisions).hasSize(2);
        assertThat(revisions.get(0).versionNumber()).isEqualTo(2);
        assertThat(revisions.get(1).versionNumber()).isEqualTo(1);
        assertThat(revisions.get(0).createdBy()).isEqualTo("testuser");
    }

    @Test
    void getRevisions_respectsLimit() {
        UUID fileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FileEntity file = new FileEntity();
        file.setId(fileId);

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");

        FileVersionEntity v3 = new FileVersionEntity();
        v3.setId(UUID.randomUUID());
        v3.setFileId(fileId);
        v3.setVersionNumber(3);
        v3.setTotalSize(3072L);
        v3.setBlockCount(3);
        v3.setCreatedBy(userId);
        v3.setCreatedAt(LocalDateTime.of(2025, 1, 3, 0, 0));

        FileVersionEntity v2 = new FileVersionEntity();
        v2.setId(UUID.randomUUID());
        v2.setFileId(fileId);
        v2.setVersionNumber(2);
        v2.setTotalSize(2048L);
        v2.setBlockCount(2);
        v2.setCreatedBy(userId);
        v2.setCreatedAt(LocalDateTime.of(2025, 1, 2, 0, 0));

        FileVersionEntity v1 = new FileVersionEntity();
        v1.setId(UUID.randomUUID());
        v1.setFileId(fileId);
        v1.setVersionNumber(1);
        v1.setTotalSize(1024L);
        v1.setBlockCount(1);
        v1.setCreatedBy(userId);
        v1.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));

        when(fileRepository.findByFilePath("/docs/test.txt")).thenReturn(Optional.of(file));
        when(fileVersionRepository.findByFileIdOrderByVersionNumberDesc(fileId))
                .thenReturn(List.of(v3, v2, v1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        List<RevisionInfo> revisions = fileVersionService.getRevisions("/docs/test.txt", 2);

        assertThat(revisions).hasSize(2);
        assertThat(revisions.get(0).versionNumber()).isEqualTo(3);
        assertThat(revisions.get(1).versionNumber()).isEqualTo(2);
    }

    @Test
    void getRevisions_returnsEmptyForNonExistentFile() {
        when(fileRepository.findByFilePath("/nonexistent/file.txt")).thenReturn(Optional.empty());

        List<RevisionInfo> revisions = fileVersionService.getRevisions("/nonexistent/file.txt", 10);

        assertThat(revisions).isEmpty();
    }
}
