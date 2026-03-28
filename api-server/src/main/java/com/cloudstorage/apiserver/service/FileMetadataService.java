package com.cloudstorage.apiserver.service;

import com.cloudstorage.apiserver.entity.FileEntity;
import com.cloudstorage.apiserver.repository.FileRepository;
import com.cloudstorage.common.dto.FileMetadataDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FileMetadataService {

    private final FileRepository fileRepository;
    private final Counter cacheHitsCounter;
    private final Counter cacheMissesCounter;

    public FileMetadataService(FileRepository fileRepository, MeterRegistry meterRegistry) {
        this.fileRepository = fileRepository;
        this.cacheHitsCounter = Counter.builder("cloud_storage_cache_hits_total")
                .description("Total cache hits")
                .register(meterRegistry);
        this.cacheMissesCounter = Counter.builder("cloud_storage_cache_misses_total")
                .description("Total cache misses")
                .register(meterRegistry);
    }

    @Cacheable(value = "fileMetadata", key = "#path")
    public FileMetadataDto getFileByPath(String path) {
        log.debug("Cache miss for path: {}", path);
        cacheMissesCounter.increment();

        FileEntity file = fileRepository.findByFilePath(path)
                .orElseThrow(() -> new RuntimeException("File not found: " + path));

        return new FileMetadataDto(
                file.getId(),
                file.getFilename(),
                file.getFilePath(),
                file.getFileSize(),
                file.getLatestVersion(),
                file.getStatus()
        );
    }

    @CacheEvict(value = "fileMetadata", key = "#path")
    public void evictCache(String path) {
        log.debug("Evicting cache for path: {}", path);
    }

    public void recordCacheHit() {
        cacheHitsCounter.increment();
    }
}
