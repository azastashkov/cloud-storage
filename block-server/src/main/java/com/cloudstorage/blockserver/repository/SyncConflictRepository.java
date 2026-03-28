package com.cloudstorage.blockserver.repository;

import com.cloudstorage.blockserver.entity.SyncConflictEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SyncConflictRepository extends JpaRepository<SyncConflictEntity, UUID> {
}
