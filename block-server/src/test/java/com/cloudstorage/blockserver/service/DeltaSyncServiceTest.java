package com.cloudstorage.blockserver.service;

import com.cloudstorage.blockserver.entity.BlockEntity;
import com.cloudstorage.blockserver.repository.BlockRepository;
import com.cloudstorage.common.dto.BlockInfo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeltaSyncServiceTest {

    @Mock
    private BlockRepository blockRepository;

    private DeltaSyncService deltaSyncService;

    @BeforeEach
    void setUp() {
        deltaSyncService = new DeltaSyncService(blockRepository, new SimpleMeterRegistry());
    }

    @Test
    void findBlocksToUpload_someHashesExist_returnsOnlyMissing() {
        List<BlockInfo> clientBlocks = List.of(
                new BlockInfo(0, "hash0", 100),
                new BlockInfo(1, "hash1", 100),
                new BlockInfo(2, "hash2", 100),
                new BlockInfo(3, "hash3", 100),
                new BlockInfo(4, "hash4", 100)
        );

        BlockEntity existing0 = new BlockEntity();
        existing0.setBlockHash("hash0");
        BlockEntity existing2 = new BlockEntity();
        existing2.setBlockHash("hash2");
        BlockEntity existing4 = new BlockEntity();
        existing4.setBlockHash("hash4");

        when(blockRepository.findByBlockHashIn(anyList()))
                .thenReturn(List.of(existing0, existing2, existing4));

        List<Integer> result = deltaSyncService.findBlocksToUpload(clientBlocks);

        assertEquals(2, result.size());
        assertTrue(result.contains(1));
        assertTrue(result.contains(3));
    }

    @Test
    void findBlocksToUpload_allHashesExist_returnsEmptyList() {
        List<BlockInfo> clientBlocks = List.of(
                new BlockInfo(0, "hash0", 100),
                new BlockInfo(1, "hash1", 100),
                new BlockInfo(2, "hash2", 100)
        );

        BlockEntity existing0 = new BlockEntity();
        existing0.setBlockHash("hash0");
        BlockEntity existing1 = new BlockEntity();
        existing1.setBlockHash("hash1");
        BlockEntity existing2 = new BlockEntity();
        existing2.setBlockHash("hash2");

        when(blockRepository.findByBlockHashIn(anyList()))
                .thenReturn(List.of(existing0, existing1, existing2));

        List<Integer> result = deltaSyncService.findBlocksToUpload(clientBlocks);

        assertTrue(result.isEmpty());
    }

    @Test
    void findBlocksToUpload_noHashesExist_returnsAllBlockOrders() {
        List<BlockInfo> clientBlocks = List.of(
                new BlockInfo(0, "hash0", 100),
                new BlockInfo(1, "hash1", 100),
                new BlockInfo(2, "hash2", 100)
        );

        when(blockRepository.findByBlockHashIn(anyList()))
                .thenReturn(Collections.emptyList());

        List<Integer> result = deltaSyncService.findBlocksToUpload(clientBlocks);

        assertEquals(3, result.size());
        assertTrue(result.contains(0));
        assertTrue(result.contains(1));
        assertTrue(result.contains(2));
    }
}
