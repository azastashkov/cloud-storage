package com.cloudstorage.apiserver.repository;

import com.cloudstorage.apiserver.entity.SyncConflictEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SyncConflictRepository extends JpaRepository<SyncConflictEntity, UUID> {

    List<SyncConflictEntity> findByFileIdAndStatus(UUID fileId, String status);

    List<SyncConflictEntity> findByUserIdAndStatus(UUID userId, String status);
}
