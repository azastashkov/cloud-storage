package com.cloudstorage.blockserver.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class BlockSplitterServiceTest {

    private static final int BLOCK_SIZE = 4 * 1024 * 1024; // 4MB

    private final BlockSplitterService splitterService = new BlockSplitterService(BLOCK_SIZE);

    @Test
    void split_18MBFile_returns5Blocks() throws IOException {
        int fileSize = 18 * 1024 * 1024; // 18MB
        byte[] data = new byte[fileSize];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);

        List<byte[]> blocks = splitterService.split(inputStream, fileSize);

        assertEquals(5, blocks.size());
        assertEquals(BLOCK_SIZE, blocks.get(0).length);
        assertEquals(BLOCK_SIZE, blocks.get(1).length);
        assertEquals(BLOCK_SIZE, blocks.get(2).length);
        assertEquals(BLOCK_SIZE, blocks.get(3).length);
        assertEquals(2 * 1024 * 1024, blocks.get(4).length);
    }

    @Test
    void split_exactly4MB_returns1Block() throws IOException {
        byte[] data = new byte[BLOCK_SIZE];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);

        List<byte[]> blocks = splitterService.split(inputStream, BLOCK_SIZE);

        assertEquals(1, blocks.size());
        assertEquals(BLOCK_SIZE, blocks.get(0).length);
    }

    @Test
    void split_emptyInput_returns0Blocks() throws IOException {
        byte[] data = new byte[0];
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);

        List<byte[]> blocks = splitterService.split(inputStream, 0);

        assertTrue(blocks.isEmpty());
    }
}
