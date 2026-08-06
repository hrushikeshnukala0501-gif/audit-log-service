package com.auditlog.infrastructure.persistence.repository;
import com.auditlog.infrastructure.persistence.entity.PayloadRedactionEntity; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface PayloadRedactionRepository extends JpaRepository<PayloadRedactionEntity,UUID>{ List<PayloadRedactionEntity> findByTargetEventIdIn(Collection<UUID> ids); boolean existsByTargetEventIdAndJsonPointer(UUID id,String pointer); }
