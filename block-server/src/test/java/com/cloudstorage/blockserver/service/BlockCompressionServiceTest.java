package com.cloudstorage.blockserver.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class BlockCompressionServiceTest {

    private final BlockCompressionService compressionService = new BlockCompressionService();

    @Test
    void compressDecompress_textData_roundtrip() throws IOException {
        String text = "This is a test string that should compress well because it has repeated patterns. "
                .repeat(100);
        byte[] originalData = text.getBytes();

        byte[] compressed = compressionService.compress(originalData);
        byte[] decompressed = compressionService.decompress(compressed);

        assertArrayEquals(originalData, decompressed);
        assertTrue(compressed.length < originalData.length,
                "Compressed data should be smaller for repetitive text");
    }

    @Test
    void compressDecompress_randomBytes_roundtrip() throws IOException {
        Random random = new Random(42);
        byte[] originalData = new byte[1024];
        random.nextBytes(originalData);

        byte[] compressed = compressionService.compress(originalData);
        byte[] decompressed = compressionService.decompress(compressed);

        assertArrayEquals(originalData, decompressed);
    }
}
