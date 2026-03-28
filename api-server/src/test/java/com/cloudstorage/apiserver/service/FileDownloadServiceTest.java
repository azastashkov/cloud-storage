package com.cloudstorage.apiserver.service;

import com.cloudstorage.apiserver.entity.FileEntity;
import com.cloudstorage.apiserver.entity.FileVersionEntity;
import com.cloudstorage.apiserver.repository.BlockRepository;
import com.cloudstorage.apiserver.repository.FileRepository;
import com.cloudstorage.apiserver.repository.FileVersionBlockRepository;
import com.cloudstorage.apiserver.repository.FileVersionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileDownloadServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileVersionRepository fileVersionRepository;

    @Mock
    private FileVersionBlockRepository fileVersionBlockRepository;

    @Mock
    private BlockRepository blockRepository;

    @Mock
    private ColdStorageService coldStorageService;

    @Mock
    private RestTemplate restTemplate;

    private FileDownloadService fileDownloadService;

    @BeforeEach
    void setUp() {
        fileDownloadService = new FileDownloadService(
                fileRepository,
                fileVersionRepository,
                fileVersionBlockRepository,
                blockRepository,
                coldStorageService,
                restTemplate,
                new SimpleMeterRegistry()
        );
        ReflectionTestUtils.setField(fileDownloadService, "blockServerUrl", "http://localhost:8081");
    }

    @Test
    void downloadFile_callsBlockServerAndReturnsBytes() {
        UUID fileId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setFilePath("/docs/test.txt");
        file.setLatestVersion(1);

        FileVersionEntity version = new FileVersionEntity();
        version.setId(versionId);
        version.setFileId(fileId);
        version.setVersionNumber(1);

        byte[] expectedData = "file content".getBytes();

        when(fileRepository.findByFilePath("/docs/test.txt")).thenReturn(Optional.of(file));
        when(fileVersionRepository.findByFileIdAndVersionNumber(fileId, 1))
                .thenReturn(Optional.of(version));
        when(fileVersionBlockRepository.findByFileVersionIdOrderByBlockOrderAsc(versionId))
                .thenReturn(Collections.emptyList());
        when(restTemplate.getForObject(
                eq("http://localhost:8081/internal/blocks/assemble?fileVersionId=" + versionId),
                eq(byte[].class)))
                .thenReturn(expectedData);

        byte[] result = fileDownloadService.downloadFile("/docs/test.txt");

        assertThat(result).isEqualTo(expectedData);
    }

    @Test
    void downloadFile_throwsForNonExistentPath() {
        when(fileRepository.findByFilePath("/nonexistent/file.txt")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileDownloadService.downloadFile("/nonexistent/file.txt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("File not found");
    }
}
