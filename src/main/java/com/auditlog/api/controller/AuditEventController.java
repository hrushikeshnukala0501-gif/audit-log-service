package com.auditlog.api.controller;

import com.auditlog.api.dto.AuditEventResponse;
import com.auditlog.api.dto.CreateAuditEventRequest;
import com.auditlog.api.dto.VerificationResponse;
import com.auditlog.api.response.ApiResponse;
import com.auditlog.application.command.AppendAuditEventCommand;
import com.auditlog.application.query.AuditEventSearchCriteria;
import com.auditlog.application.query.SortDirection;
import com.auditlog.application.result.AppendedAuditEvent;
import com.auditlog.application.result.AuditEventPage;
import com.auditlog.application.result.ChainVerificationResult;
import com.auditlog.application.service.AuditChainVerificationService;
import com.auditlog.application.service.AuditEventAppendService;
import com.auditlog.application.service.AuditEventQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * HTTP adapter for the three required Scenario A audit-log operations.
 */
@Validated
@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit Log Service", description = "Append-only audit event, query, and chain verification operations")
public class AuditEventController {

    private static final String TRACE_ID_KEY = "traceId";

    private final AuditEventAppendService appendService;
    private final AuditEventQueryService queryService;
    private final AuditChainVerificationService verificationService;

    public AuditEventController(
            AuditEventAppendService appendService,
            AuditEventQueryService queryService,
            AuditChainVerificationService verificationService) {
        this.appendService = appendService;
        this.queryService = queryService;
        this.verificationService = verificationService;
    }

    @PostMapping("/events")
    @Operation(
            summary = "Append an audit event",
            description = "Creates one immutable audit event. The service assigns the authoritative UTC timestamp and links the event to the prior hash. Updates and deletes are intentionally unavailable.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Audit event appended",
                    content = @Content(examples = @ExampleObject(value = """
                            {"timestamp":"2026-08-06T10:00:00Z","traceId":"f47ac10b-58cc-4372-a567-0e02b2c3d479","data":{"eventId":"11111111-1111-1111-1111-111111111111","chainSequence":1,"eventType":"RECORD_UPDATED","actorId":"user-123","resourceType":"ACCOUNT","resourceId":"account-456","recordedAt":"2026-08-06T10:00:00Z","payload":{"field":"address","source":"customer-portal"},"previousHash":"6f631d53cdc1f26efef6d78054e78948c5681f71267c3f9370cf7e5a7a134b39","contentHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","hashAlgorithm":"SHA-256","hashVersion":1}}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation or JSON request error",
                    content = @Content(examples = @ExampleObject(value = """
                            {"timestamp":"2026-08-06T10:00:00Z","traceId":"f47ac10b-58cc-4372-a567-0e02b2c3d479","status":400,"code":"VALIDATION_FAILED","message":"Request validation failed","violations":[{"field":"eventType","message":"eventType is required"}]}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Integrity or persistence conflict", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content)
    })
    public ResponseEntity<ApiResponse<AuditEventResponse>> append(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "eventType": "RECORD_UPDATED",
                              "actorId": "user-123",
                              "resourceType": "ACCOUNT",
                              "resourceId": "account-456",
                              "payload": {"field": "address", "source": "customer-portal"}
                            }
                            """)))
            @Valid @RequestBody CreateAuditEventRequest request) {
        AppendedAuditEvent appended = appendService.append(new AppendAuditEventCommand(
                request.eventType(), request.actorId(), request.resourceType(), request.resourceId(), request.payload()));
        AuditEventResponse response = new AuditEventResponse(
                appended.eventId(), appended.chainSequence(), request.eventType(), request.actorId(), request.resourceType(),
                request.resourceId(), appended.recordedAt(), request.payload(), appended.previousHash(), appended.contentHash(),
                appended.hashAlgorithm(), appended.hashVersion());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, traceId()));
    }

    @GetMapping("/events")
    @Operation(
            summary = "Query audit events",
            description = "Retrieves audit events with any combination of actor, resource, event type, and inclusive UTC time-range filters. Pagination uses an opaque cursor on immutable chain sequence.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Matching audit event page",
                    content = @Content(examples = @ExampleObject(value = """
                            {"timestamp":"2026-08-06T10:00:00Z","traceId":"f47ac10b-58cc-4372-a567-0e02b2c3d479","data":{"events":[{"eventId":"11111111-1111-1111-1111-111111111111","chainSequence":1,"eventType":"RECORD_UPDATED","actorId":"user-123","resourceType":"ACCOUNT","resourceId":"account-456","recordedAt":"2026-08-06T10:00:00Z","payload":{"field":"address"},"previousHash":"6f631d53cdc1f26efef6d78054e78948c5681f71267c3f9370cf7e5a7a134b39","contentHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","hashAlgorithm":"SHA-256","hashVersion":1}],"nextCursor":"QVNDOjE"}}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid filter, time range, cursor, or page size", content = @Content)
    })
    public ApiResponse<AuditEventPage> find(
            @Parameter(description = "Exact actor identifier") @RequestParam(required = false) @Size(max = 255) String actorId,
            @Parameter(description = "Exact resource type") @RequestParam(required = false) @Size(max = 100) String resourceType,
            @Parameter(description = "Exact resource identifier") @RequestParam(required = false) @Size(max = 255) String resourceId,
            @Parameter(description = "Exact event type") @RequestParam(required = false) @Size(max = 100) String eventType,
            @Parameter(description = "Inclusive UTC start time") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Inclusive UTC end time") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Opaque cursor returned by a previous page") @RequestParam(required = false) String cursor,
            @Parameter(description = "Results per page, from 1 through 100") @RequestParam(defaultValue = "50") @Min(1) @Max(100) int pageSize,
            @Parameter(description = "Sort by immutable chain sequence") @RequestParam(defaultValue = "ASC") SortDirection sortDirection) {
        return ApiResponse.success(queryService.find(new AuditEventSearchCriteria(
                actorId, resourceType, resourceId, eventType, from, to, cursor, pageSize, sortDirection)), traceId());
    }

    @GetMapping("/verify")
    @Operation(
            summary = "Verify the audit hash chain",
            description = "Without bounds, streams and validates the complete chain including the final chain head. Optional sequence bounds validate only that range and its immediate predecessor continuity; a bounded result is not a complete-chain verification.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Verification completed; inspect `data.intact` for the result",
                    content = @Content(examples = @ExampleObject(value = """
                            {"timestamp":"2026-08-06T10:00:00Z","traceId":"f47ac10b-58cc-4372-a567-0e02b2c3d479","data":{"intact":false,"verifiedThroughSequence":4,"violation":{"eventId":"22222222-2222-2222-2222-222222222222","chainSequence":5,"type":"CONTENT_HASH_MISMATCH","message":"Stored event content does not match its content hash"},"verifiedAt":"2026-08-06T10:00:01Z"}}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Verification could not complete", content = @Content)
    })
    public ApiResponse<VerificationResponse> verify(
            @Parameter(description = "Optional inclusive first chain sequence; must be positive")
            @RequestParam(required = false) @Min(1) Long fromSequence,
            @Parameter(description = "Optional inclusive last chain sequence; must be positive")
            @RequestParam(required = false) @Min(1) Long toSequence) {
        ChainVerificationResult result = verificationService.verify(fromSequence, toSequence);
        VerificationResponse.VerificationViolation violation = result.violation() == null ? null
                : new VerificationResponse.VerificationViolation(
                result.violation().eventId(), result.violation().chainSequence(), result.violation().type(), result.violation().message());
        return ApiResponse.success(new VerificationResponse(
                result.intact(),
                result.completeChainVerification(),
                result.verifiedFromSequence(),
                result.verifiedThroughSequence(),
                violation,
                result.verifiedAt()), traceId());
    }

    private String traceId() {
        return MDC.get(TRACE_ID_KEY);
    }
}
