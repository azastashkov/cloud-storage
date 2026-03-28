package com.cloudstorage.apiserver.service;

import com.cloudstorage.apiserver.entity.BlockEntity;
import com.cloudstorage.apiserver.repository.BlockRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ColdStorageServiceTest {

    @Mock
    private BlockRepository blockRepository;

    @Mock
    private MinioClient minioClient;

    private ColdStorageService coldStorageService;

    @BeforeEach
    void setUp() {
        coldStorageService = new ColdStorageService(blockRepository, minioClient, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(coldStorageService, "thresholdDays", 30);
        ReflectionTestUtils.setField(coldStorageService, "blockBucket", "block-storage");
        ReflectionTestUtils.setField(coldStorageService, "coldBucket", "cold-storage");
    }

    @Test
    void moveBlocksToColdStorage_movesOldBlocks() throws Exception {
        BlockEntity oldBlock = new BlockEntity();
        oldBlock.setId(UUID.randomUUID());
        oldBlock.setBlockHash("hash123");
        oldBlock.setMinioKey("blocks/hash123");
        oldBlock.setStorageTier("HOT");
        oldBlock.setLastAccessedAt(LocalDateTime.now().minusDays(60));

        when(blockRepository.findByStorageTierAndLastAccessedAtBefore(any(), any()))
                .thenReturn(List.of(oldBlock));
        when(minioClient.copyObject(any())).thenReturn(new ObjectWriteResponse(null, "cold-storage", null, "blocks/hash123", null, null));
        when(blockRepository.save(any())).thenReturn(oldBlock);

        coldStorageService.moveBlocksToColdStorage();

        verify(minioClient).copyObject(any());
        verify(blockRepository).save(oldBlock);
        assertThat(oldBlock.getStorageTier()).isEqualTo("COLD");
    }

    @Test
    void moveBlocksToColdStorage_skipsRecentBlocks() throws Exception {
        when(blockRepository.findByStorageTierAndLastAccessedAtBefore(any(), any()))
                .thenReturn(List.of());

        coldStorageService.moveBlocksToColdStorage();

        verify(minioClient, never()).copyObject(any());
    }

    @Test
    void warmUpBlock_copiesFromColdToHot() throws Exception {
        BlockEntity coldBlock = new BlockEntity();
        coldBlock.setId(UUID.randomUUID());
        coldBlock.setBlockHash("hash456");
        coldBlock.setMinioKey("blocks/hash456");
        coldBlock.setStorageTier("COLD");

        when(minioClient.copyObject(any())).thenReturn(new ObjectWriteResponse(null, "block-storage", null, "blocks/hash456", null, null));
        when(blockRepository.save(any())).thenReturn(coldBlock);

        coldStorageService.warmUpBlock(coldBlock);

        verify(minioClient).copyObject(any());
        verify(blockRepository).save(coldBlock);
        assertThat(coldBlock.getStorageTier()).isEqualTo("HOT");
        assertThat(coldBlock.getLastAccessedAt()).isNotNull();
    }
}
