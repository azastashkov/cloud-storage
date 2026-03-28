package com.cloudstorage.blockserver.controller;

import com.cloudstorage.blockserver.service.DeltaSyncService;
import com.cloudstorage.blockserver.service.FileAssemblyService;
import com.cloudstorage.blockserver.service.FileUploadService;
import com.cloudstorage.common.dto.BlockInfo;
import com.cloudstorage.common.dto.UploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class BlockController {

    private final FileUploadService fileUploadService;
    private final FileAssemblyService fileAssemblyService;
    private final DeltaSyncService deltaSyncService;

    @PostMapping("/files/upload")
    public ResponseEntity<UploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("filename") String filename,
            @RequestParam(value = "userId", defaultValue = "00000000-0000-0000-0000-000000000001") UUID userId,
            @RequestParam(value = "expectedVersion", required = false) Integer expectedVersion)
            throws IOException {

        UploadResponse response;
        if (expectedVersion != null) {
            response = fileUploadService.uploadFileWithExpectedVersion(
                    file.getInputStream(), file.getSize(), filename, userId, expectedVersion);
        } else {
            response = fileUploadService.uploadFile(
                    file.getInputStream(), file.getSize(), filename, userId);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/internal/blocks/assemble")
    public ResponseEntity<byte[]> assembleFile(@RequestParam("fileVersionId") UUID fileVersionId)
            throws IOException {

        byte[] fileBytes = fileAssemblyService.assembleFile(fileVersionId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(fileBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(fileBytes);
    }

    @PostMapping("/internal/blocks/delta-check")
    public ResponseEntity<List<Integer>> deltaCheck(@RequestBody List<BlockInfo> blockInfos) {
        List<Integer> blocksToUpload = deltaSyncService.findBlocksToUpload(blockInfos);
        return ResponseEntity.ok(blocksToUpload);
    }
}
