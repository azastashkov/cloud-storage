package com.cloudstorage.blockserver.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class BlockHashServiceTest {

    private final BlockHashService hashService = new BlockHashService();

    @Test
    void computeHash_knownInput_producesExpectedHash() {
        byte[] data = "hello".getBytes();

        String hash = hashService.computeHash(data);

        // SHA-256 of "hello"
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hash);
    }

    @Test
    void computeHash_sameInput_producesSameHash() {
        byte[] data1 = "test data".getBytes();
        byte[] data2 = "test data".getBytes();

        String hash1 = hashService.computeHash(data1);
        String hash2 = hashService.computeHash(data2);

        assertEquals(hash1, hash2);
    }

    @Test
    void computeHash_differentInputs_produceDifferentHashes() {
        byte[] data1 = "input one".getBytes();
        byte[] data2 = "input two".getBytes();

        String hash1 = hashService.computeHash(data1);
        String hash2 = hashService.computeHash(data2);

        assertNotEquals(hash1, hash2);
    }
}
