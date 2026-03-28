package com.cloudstorage.apiserver.controller;

import com.cloudstorage.apiserver.service.ConflictResolutionService;
import com.cloudstorage.apiserver.service.FileDownloadService;
import com.cloudstorage.apiserver.service.FileVersionService;
import com.cloudstorage.common.dto.RevisionInfo;
import com.cloudstorage.common.dto.SyncConflictDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileDownloadService fileDownloadService;
    private final FileVersionService fileVersionService;
    private final ConflictResolutionService conflictResolutionService;

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadFile(@RequestParam("path") String path) {
        byte[] data = fileDownloadService.downloadFile(path);

        String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(data);
    }

    @GetMapping("/list_revisions")
    public ResponseEntity<List<RevisionInfo>> listRevisions(
            @RequestParam("path") String path,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        List<RevisionInfo> revisions = fileVersionService.getRevisions(path, limit);
        return ResponseEntity.ok(revisions);
    }

    @GetMapping("/conflicts")
    public ResponseEntity<List<SyncConflictDto>> getConflicts(
            @RequestParam("userId") UUID userId) {
        List<SyncConflictDto> conflicts = conflictResolutionService.getConflicts(userId);
        return ResponseEntity.ok(conflicts);
    }

    @PostMapping("/conflicts/{id}/resolve")
    public ResponseEntity<Void> resolveConflict(
            @PathVariable("id") UUID conflictId,
            @RequestParam("resolution") String resolution) {
        conflictResolutionService.resolveConflict(conflictId, resolution);
        return ResponseEntity.noContent().build();
    }
}
