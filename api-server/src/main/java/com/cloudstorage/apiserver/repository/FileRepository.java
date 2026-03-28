package com.cloudstorage.apiserver.repository;

import com.cloudstorage.apiserver.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, UUID> {

    Optional<FileEntity> findByUserIdAndFilePath(UUID userId, String filePath);

    Optional<FileEntity> findByFilePath(String filePath);
}
