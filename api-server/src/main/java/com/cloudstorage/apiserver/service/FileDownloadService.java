package com.cloudstorage.apiserver.service;

import com.cloudstorage.apiserver.entity.BlockEntity;
import com.cloudstorage.apiserver.entity.FileEntity;
import com.cloudstorage.apiserver.entity.FileVersionBlockEntity;
import com.cloudstorage.apiserver.entity.FileVersionEntity;
import com.cloudstorage.apiserver.repository.BlockRepository;
import com.cloudstorage.apiserver.repository.FileRepository;
import com.cloudstorage.apiserver.repository.FileVersionBlockRepository;
import com.cloudstorage.apiserver.repository.FileVersionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FileDownloadService {

    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final FileVersionBlockRepository fileVersionBlockRepository;
    private final BlockRepository blockRepository;
    private final ColdStorageService coldStorageService;
    private final RestTemplate restTemplate;
    private final Counter downloadsCounter;
    private final DistributionSummary downloadSizeSummary;

    @Value("${block-server.url}")
    private String blockServerUrl;

    public FileDownloadService(FileRepository fileRepository,
                               FileVersionRepository fileVersionRepository,
                               FileVersionBlockRepository fileVersionBlockRepository,
                               BlockRepository blockRepository,
                               ColdStorageService coldStorageService,
                               RestTemplate restTemplate,
                               MeterRegistry meterRegistry) {
        this.fileRepository = fileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.fileVersionBlockRepository = fileVersionBlockRepository;
        this.blockRepository = blockRepository;
        this.coldStorageService = coldStorageService;
        this.restTemplate = restTemplate;
        this.downloadsCounter = Counter.builder("cloud_storage_file_downloads_total")
                .description("Total file downloads")
                .register(meterRegistry);
        this.downloadSizeSummary = DistributionSummary.builder("cloud_storage_download_size_bytes")
                .description("Download file sizes in bytes")
                .register(meterRegistry);
    }

    public byte[] downloadFile(String path) {
        FileEntity file = fileRepository.findByFilePath(path)
                .orElseThrow(() -> new RuntimeException("File not found: " + path));

        FileVersionEntity latestVersion = fileVersionRepository
                .findByFileIdAndVersionNumber(file.getId(), file.getLatestVersion())
                .orElseThrow(() -> new RuntimeException("Latest version not found for file: " + path));

        return downloadFileVersion(latestVersion.getId());
    }

    public byte[] downloadFileVersion(UUID fileVersionId) {
        warmUpColdBlocks(fileVersionId);

        String url = blockServerUrl + "/internal/blocks/assemble?fileVersionId=" + fileVersionId;
        byte[] data = restTemplate.getForObject(url, byte[].class);

        if (data != null) {
            downloadsCounter.increment();
            downloadSizeSummary.record(data.length);
        }

        return data;
    }

    private void warmUpColdBlocks(UUID fileVersionId) {
        List<FileVersionBlockEntity> versionBlocks = fileVersionBlockRepository
                .findByFileVersionIdOrderByBlockOrderAsc(fileVersionId);

        for (FileVersionBlockEntity vb : versionBlocks) {
            BlockEntity block = blockRepository.findById(vb.getBlockId()).orElse(null);
            if (block != null && "COLD".equals(block.getStorageTier())) {
                log.info("Warming up cold block {} for download", block.getId());
                coldStorageService.warmUpBlock(block);
            }
        }
    }
}
