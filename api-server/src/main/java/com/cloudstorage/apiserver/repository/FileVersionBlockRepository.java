package com.cloudstorage.apiserver.repository;

import com.cloudstorage.apiserver.entity.FileVersionBlockEntity;
import com.cloudstorage.apiserver.entity.FileVersionBlockId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FileVersionBlockRepository extends JpaRepository<FileVersionBlockEntity, FileVersionBlockId> {

    List<FileVersionBlockEntity> findByFileVersionIdOrderByBlockOrderAsc(UUID fileVersionId);
}
