package com.auditlog.infrastructure.persistence.repository;
import com.auditlog.infrastructure.persistence.entity.ArchiveManifestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface ArchiveManifestRepository extends JpaRepository<ArchiveManifestEntity, UUID> {
    Optional<ArchiveManifestEntity> findTopByOrderByToSequenceDesc();
}
