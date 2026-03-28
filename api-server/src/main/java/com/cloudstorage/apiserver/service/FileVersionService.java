package com.cloudstorage.apiserver.service;

import com.cloudstorage.apiserver.entity.FileEntity;
import com.cloudstorage.apiserver.entity.FileVersionEntity;
import com.cloudstorage.apiserver.entity.UserEntity;
import com.cloudstorage.apiserver.repository.FileRepository;
import com.cloudstorage.apiserver.repository.FileVersionRepository;
import com.cloudstorage.apiserver.repository.UserRepository;
import com.cloudstorage.common.dto.RevisionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileVersionService {

    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final UserRepository userRepository;

    public List<RevisionInfo> getRevisions(String path, int limit) {
        Optional<FileEntity> fileOpt = fileRepository.findByFilePath(path);
        if (fileOpt.isEmpty()) {
            return Collections.emptyList();
        }

        FileEntity file = fileOpt.get();
        List<FileVersionEntity> versions = fileVersionRepository
                .findByFileIdOrderByVersionNumberDesc(file.getId());

        return versions.stream()
                .limit(limit)
                .map(v -> {
                    String username = userRepository.findById(v.getCreatedBy())
                            .map(UserEntity::getUsername)
                            .orElse("unknown");
                    return new RevisionInfo(
                            v.getId(),
                            v.getVersionNumber(),
                            v.getTotalSize(),
                            v.getBlockCount(),
                            v.getCreatedAt() != null
                                    ? v.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toInstant()
                                    : null,
                            username
                    );
                })
                .toList();
    }
}
