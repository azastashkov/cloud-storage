package com.cloudstorage.apiserver.service;

import com.cloudstorage.apiserver.entity.FileEntity;
import com.cloudstorage.apiserver.repository.FileRepository;
import com.cloudstorage.common.dto.FileMetadataDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileMetadataServiceTest {

    @Mock
    private FileRepository fileRepository;

    private FileMetadataService fileMetadataService;

    @BeforeEach
    void setUp() {
        fileMetadataService = new FileMetadataService(fileRepository, new SimpleMeterRegistry());
    }

    @Test
    void getFileByPath_returnsCorrectDto() {
        UUID fileId = UUID.randomUUID();
        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setFilename("test.txt");
        file.setFilePath("/docs/test.txt");
        file.setFileSize(1024L);
        file.setLatestVersion(3);
        file.setStatus("ACTIVE");
        file.setCreatedAt(LocalDateTime.now());
        file.setUpdatedAt(LocalDateTime.now());

        when(fileRepository.findByFilePath("/docs/test.txt")).thenReturn(Optional.of(file));

        FileMetadataDto result = fileMetadataService.getFileByPath("/docs/test.txt");

        assertThat(result.id()).isEqualTo(fileId);
        assertThat(result.filename()).isEqualTo("test.txt");
        assertThat(result.path()).isEqualTo("/docs/test.txt");
        assertThat(result.size()).isEqualTo(1024L);
        assertThat(result.latestVersion()).isEqualTo(3);
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void getFileByPath_throwsWhenFileNotFound() {
        when(fileRepository.findByFilePath("/nonexistent/file.txt")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileMetadataService.getFileByPath("/nonexistent/file.txt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("File not found");
    }
}
