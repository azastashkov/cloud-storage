package com.cloudstorage.apiserver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "file_version_blocks")
@IdClass(FileVersionBlockId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileVersionBlockEntity {

    @Id
    @Column(name = "file_version_id", nullable = false)
    private UUID fileVersionId;

    @Column(name = "block_id", nullable = false)
    private UUID blockId;

    @Id
    @Column(name = "block_order", nullable = false)
    private int blockOrder;
}
