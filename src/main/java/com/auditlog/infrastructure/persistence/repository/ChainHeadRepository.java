package com.auditlog.infrastructure.persistence.repository;

import com.auditlog.infrastructure.persistence.entity.ChainHeadEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * Provides the pessimistic lock required to serialize global chain appends.
 */
public interface ChainHeadRepository extends JpaRepository<ChainHeadEntity, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select chainHead from ChainHeadEntity chainHead where chainHead.chainId = :chainId")
    Optional<ChainHeadEntity> findByChainIdForUpdate(short chainId);
}
