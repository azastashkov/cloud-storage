package com.cloudstorage.blockserver.service;

import com.cloudstorage.blockserver.entity.BlockEntity;
import com.cloudstorage.blockserver.entity.FileVersionBlockEntity;
import com.cloudstorage.blockserver.repository.BlockRepository;
import com.cloudstorage.blockserver.repository.FileVersionBlockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileAssemblyService {

    private final FileVersionBlockRepository fileVersionBlockRepository;
    private final BlockRepository blockRepository;
    private final BlockStorageService blockStorageService;
    private final BlockCompressionService blockCompressionService;

    public byte[] assembleFile(UUID fileVersionId) throws IOException {
        List<FileVersionBlockEntity> versionBlocks =
                fileVersionBlockRepository.findByFileVersionIdOrderByBlockOrderAsc(fileVersionId);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        for (FileVersionBlockEntity versionBlock : versionBlocks) {
            BlockEntity blockEntity = blockRepository.findById(versionBlock.getBlockId())
                    .orElseThrow(() -> new RuntimeException(
                            "Block not found: " + versionBlock.getBlockId()));

            byte[] compressedData = blockStorageService.retrieveBlock(blockEntity.getBlockHash());
            byte[] decompressedData = blockCompressionService.decompress(compressedData);
            outputStream.write(decompressedData);

            blockEntity.setLastAccessedAt(LocalDateTime.now());
            blockRepository.save(blockEntity);
        }

        log.debug("Assembled file version {} with {} blocks", fileVersionId, versionBlocks.size());
        return outputStream.toByteArray();
    }
}
