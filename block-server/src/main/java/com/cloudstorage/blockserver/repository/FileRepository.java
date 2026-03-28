package com.cloudstorage.blockserver.repository;

import com.cloudstorage.blockserver.entity.FileEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, UUID> {

    Optional<FileEntity> findByUserIdAndFilePath(UUID userId, String filePath);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FileEntity f WHERE f.userId = :userId AND f.filePath = :filePath")
    Optional<FileEntity> findByUserIdAndFilePathForUpdate(UUID userId, String filePath);
}
