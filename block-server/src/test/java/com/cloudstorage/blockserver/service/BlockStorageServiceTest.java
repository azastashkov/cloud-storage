package com.cloudstorage.blockserver.service;

import com.cloudstorage.blockserver.entity.BlockEntity;
import com.cloudstorage.blockserver.repository.BlockRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private BlockRepository blockRepository;

    private MeterRegistry meterRegistry;
    private BlockStorageService blockStorageService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        blockStorageService = new BlockStorageService(minioClient, blockRepository,
                meterRegistry, "block-storage");
    }

    @Test
    void storeBlock_newHash_uploadsToMinioAndSaves() throws Exception {
        String hash = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        byte[] compressedData = "compressed".getBytes();
        long originalSize = 100L;

        when(blockRepository.findByBlockHash(hash)).thenReturn(Optional.empty());

        BlockEntity savedBlock = new BlockEntity();
        savedBlock.setId(UUID.randomUUID());
        savedBlock.setBlockHash(hash);
        savedBlock.setReferenceCount(1);
        when(blockRepository.save(any(BlockEntity.class))).thenReturn(savedBlock);

        BlockEntity result = blockStorageService.storeBlock(hash, compressedData, originalSize);

        assertNotNull(result);
        verify(minioClient).putObject(any(PutObjectArgs.class));
        verify(blockRepository).save(any(BlockEntity.class));

        Counter processedCounter = meterRegistry.find("cloud_storage_blocks_processed_total").counter();
        assertNotNull(processedCounter);
        assertEquals(1.0, processedCounter.count());
    }

    @Test
    void storeBlock_existingHash_incrementsReferenceCount() throws Exception {
        String hash = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        byte[] compressedData = "compressed".getBytes();
        long originalSize = 100L;

        BlockEntity existingBlock = new BlockEntity();
        existingBlock.setId(UUID.randomUUID());
        existingBlock.setBlockHash(hash);
        existingBlock.setReferenceCount(2);
        existingBlock.setMinioKey("blocks/ab/" + hash);
        when(blockRepository.findByBlockHash(hash)).thenReturn(Optional.of(existingBlock));
        when(blockRepository.save(any(BlockEntity.class))).thenReturn(existingBlock);

        BlockEntity result = blockStorageService.storeBlock(hash, compressedData, originalSize);

        assertNotNull(result);
        assertEquals(3, existingBlock.getReferenceCount());
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));

        Counter dedupCounter = meterRegistry.find("cloud_storage_blocks_deduplicated_total").counter();
        assertNotNull(dedupCounter);
        assertEquals(1.0, dedupCounter.count());
    }

    @Test
    void retrieveBlock_existingBlock_callsMinioGetObject() throws Exception {
        String blockHash = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        String minioKey = "blocks/ab/" + blockHash;
        byte[] expectedData = "block-data".getBytes();

        BlockEntity blockEntity = new BlockEntity();
        blockEntity.setId(UUID.randomUUID());
        blockEntity.setBlockHash(blockHash);
        blockEntity.setMinioKey(minioKey);
        when(blockRepository.findByBlockHash(blockHash)).thenReturn(Optional.of(blockEntity));

        GetObjectResponse mockResponse = new GetObjectResponse(
                Headers.of(),
                "block-storage",
                "",
                minioKey,
                new ByteArrayInputStream(expectedData)
        );
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);

        byte[] result = blockStorageService.retrieveBlock(blockHash);

        assertNotNull(result);
        assertEquals(new String(expectedData), new String(result));
        verify(minioClient).getObject(any(GetObjectArgs.class));
    }
}
