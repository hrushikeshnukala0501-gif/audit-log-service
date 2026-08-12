package com.auditlog.api.controller;

import com.auditlog.api.dto.ArchiveManifestResponse;
import com.auditlog.api.dto.CreateArchiveRequest;
import com.auditlog.api.response.ApiResponse;
import com.auditlog.application.result.ArchivedAuditRange;
import com.auditlog.application.service.RetentionArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit/retention")
@Tag(name = "Audit Log Service", description = "Retention operations")
public class RetentionController {
    private final RetentionArchiveService retentionArchiveService;
    public RetentionController(RetentionArchiveService retentionArchiveService) { this.retentionArchiveService = retentionArchiveService; }
    @PostMapping("/archive")
    @Operation(summary = "Create a logical archive manifest", description = "Archives the next eligible contiguous prefix. Repeated requests do not create overlapping archive ranges.")
    public ResponseEntity<ApiResponse<ArchiveManifestResponse>> archive(@Valid @RequestBody CreateArchiveRequest request) {
        ArchivedAuditRange archive = retentionArchiveService.archiveEligible(request.archivedBy());
        if (archive == null) return ResponseEntity.noContent().build();
        return ResponseEntity.status(201).body(ApiResponse.success(new ArchiveManifestResponse(
                archive.archiveManifestId(),
                archive.fromSequence(),
                archive.toSequence(),
                archive.archiveUri(),
                archive.archiveBundleHash(),
                archive.archivedAt()), MDC.get("traceId")));
    }
}
