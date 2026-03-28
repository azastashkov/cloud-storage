package com.cloudstorage.blockserver.repository;

import com.cloudstorage.blockserver.entity.BlockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlockRepository extends JpaRepository<BlockEntity, UUID> {

    Optional<BlockEntity> findByBlockHash(String blockHash);

    List<BlockEntity> findByBlockHashIn(List<String> hashes);
}
