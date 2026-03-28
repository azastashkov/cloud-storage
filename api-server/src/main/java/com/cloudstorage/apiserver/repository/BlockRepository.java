package com.cloudstorage.apiserver.repository;

import com.cloudstorage.apiserver.entity.BlockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlockRepository extends JpaRepository<BlockEntity, UUID> {

    Optional<BlockEntity> findByBlockHash(String blockHash);

    List<BlockEntity> findByStorageTierAndLastAccessedAtBefore(String storageTier, LocalDateTime threshold);
}
