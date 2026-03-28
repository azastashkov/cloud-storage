package com.cloudstorage.blockserver.service;

import com.cloudstorage.blockserver.entity.BlockEntity;
import com.cloudstorage.blockserver.entity.FileVersionBlockEntity;
import com.cloudstorage.blockserver.repository.BlockRepository;
import com.cloudstorage.blockserver.repository.FileVersionBlockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileAssemblyServiceTest {

    @Mock
    private FileVersionBlockRepository fileVersionBlockRepository;

    @Mock
    private BlockRepository blockRepository;

    @Mock
    private BlockStorageService blockStorageService;

    @Mock
    private BlockCompressionService blockCompressionService;

    @InjectMocks
    private FileAssemblyService fileAssemblyService;

    @Test
    void assembleFile_3Blocks_returnsConcatenatedData() throws IOException {
        UUID fileVersionId = UUID.randomUUID();
        UUID blockId1 = UUID.randomUUID();
        UUID blockId2 = UUID.randomUUID();
        UUID blockId3 = UUID.randomUUID();

        FileVersionBlockEntity vb1 = new FileVersionBlockEntity(fileVersionId, blockId1, 0);
        FileVersionBlockEntity vb2 = new FileVersionBlockEntity(fileVersionId, blockId2, 1);
        FileVersionBlockEntity vb3 = new FileVersionBlockEntity(fileVersionId, blockId3, 2);

        when(fileVersionBlockRepository.findByFileVersionIdOrderByBlockOrderAsc(fileVersionId))
                .thenReturn(List.of(vb1, vb2, vb3));

        BlockEntity block1 = new BlockEntity();
        block1.setId(blockId1);
        block1.setBlockHash("hash1");
        BlockEntity block2 = new BlockEntity();
        block2.setId(blockId2);
        block2.setBlockHash("hash2");
        BlockEntity block3 = new BlockEntity();
        block3.setId(blockId3);
        block3.setBlockHash("hash3");

        when(blockRepository.findById(blockId1)).thenReturn(Optional.of(block1));
        when(blockRepository.findById(blockId2)).thenReturn(Optional.of(block2));
        when(blockRepository.findById(blockId3)).thenReturn(Optional.of(block3));

        byte[] compressed1 = "c1".getBytes();
        byte[] compressed2 = "c2".getBytes();
        byte[] compressed3 = "c3".getBytes();

        when(blockStorageService.retrieveBlock("hash1")).thenReturn(compressed1);
        when(blockStorageService.retrieveBlock("hash2")).thenReturn(compressed2);
        when(blockStorageService.retrieveBlock("hash3")).thenReturn(compressed3);

        byte[] decompressed1 = "Hello ".getBytes();
        byte[] decompressed2 = "World ".getBytes();
        byte[] decompressed3 = "Test".getBytes();

        when(blockCompressionService.decompress(compressed1)).thenReturn(decompressed1);
        when(blockCompressionService.decompress(compressed2)).thenReturn(decompressed2);
        when(blockCompressionService.decompress(compressed3)).thenReturn(decompressed3);

        when(blockRepository.save(any(BlockEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        byte[] result = fileAssemblyService.assembleFile(fileVersionId);

        byte[] expected = "Hello World Test".getBytes();
        assertArrayEquals(expected, result);
        assertEquals(expected.length, result.length);
    }
}
