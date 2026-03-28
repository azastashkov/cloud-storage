package com.cloudstorage.apiserver.repository;

import com.cloudstorage.apiserver.entity.FileVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileVersionRepository extends JpaRepository<FileVersionEntity, UUID> {

    List<FileVersionEntity> findByFileIdOrderByVersionNumberDesc(UUID fileId);

    Optional<FileVersionEntity> findByFileIdAndVersionNumber(UUID fileId, int versionNumber);

    List<FileVersionEntity> findTop10ByFileIdOrderByVersionNumberDesc(UUID fileId);
}
