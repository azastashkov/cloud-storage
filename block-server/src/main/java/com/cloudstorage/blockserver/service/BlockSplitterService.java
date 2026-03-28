package com.cloudstorage.blockserver.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class BlockSplitterService {

    private final int blockSize;

    public BlockSplitterService(@Value("${block.size}") int blockSize) {
        this.blockSize = blockSize;
    }

    public List<byte[]> split(InputStream inputStream, long fileSize) throws IOException {
        List<byte[]> blocks = new ArrayList<>();
        byte[] buffer = new byte[blockSize];
        int bytesRead;

        while ((bytesRead = readFully(inputStream, buffer)) > 0) {
            if (bytesRead == blockSize) {
                blocks.add(buffer.clone());
            } else {
                byte[] lastBlock = new byte[bytesRead];
                System.arraycopy(buffer, 0, lastBlock, 0, bytesRead);
                blocks.add(lastBlock);
            }
        }

        return blocks;
    }

    private int readFully(InputStream inputStream, byte[] buffer) throws IOException {
        int totalRead = 0;
        int bytesRead;
        while (totalRead < buffer.length &&
                (bytesRead = inputStream.read(buffer, totalRead, buffer.length - totalRead)) != -1) {
            totalRead += bytesRead;
        }
        return totalRead;
    }
}
