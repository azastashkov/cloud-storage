package com.cloudstorage.blockserver.service;

import com.cloudstorage.blockserver.entity.BlockEntity;
import com.cloudstorage.blockserver.repository.BlockRepository;
import com.cloudstorage.common.dto.BlockInfo;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DeltaSyncService {

    private final BlockRepository blockRepository;
    private final Counter deltaSyncBlocksSavedCounter;

    public DeltaSyncService(BlockRepository blockRepository, MeterRegistry meterRegistry) {
        this.blockRepository = blockRepository;
        this.deltaSyncBlocksSavedCounter = Counter.builder("cloud_storage_delta_sync_blocks_saved_total")
                .description("Total blocks saved via delta sync")
                .register(meterRegistry);
    }

    public List<Integer> findBlocksToUpload(List<BlockInfo> clientBlocks) {
        List<String> allHashes = clientBlocks.stream()
                .map(BlockInfo::hash)
                .collect(Collectors.toList());

        List<BlockEntity> existingBlocks = blockRepository.findByBlockHashIn(allHashes);
        Set<String> existingHashes = existingBlocks.stream()
                .map(BlockEntity::getBlockHash)
                .collect(Collectors.toSet());

        List<Integer> blocksToUpload = new ArrayList<>();
        for (BlockInfo block : clientBlocks) {
            if (!existingHashes.contains(block.hash())) {
                blocksToUpload.add(block.blockOrder());
            }
        }

        int savedBlocks = clientBlocks.size() - blocksToUpload.size();
        if (savedBlocks > 0) {
            deltaSyncBlocksSavedCounter.increment(savedBlocks);
        }

        log.debug("Delta sync: {} of {} blocks need upload", blocksToUpload.size(), clientBlocks.size());
        return blocksToUpload;
    }
}
