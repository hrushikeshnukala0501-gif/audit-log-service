package com.auditlog.api.exception;

import com.auditlog.api.response.ApiErrorResponse;
import com.auditlog.support.exception.AuditLogException;
import com.auditlog.support.exception.ErrorCode;
import org.slf4j.MDC;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

/**
 * Maps expected failures to stable, payload-safe API error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<ApiErrorResponse.FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldViolation)
                .toList();
        LOGGER.warn("Request validation failed for {} {}", request.getMethod(), request.getRequestURI());
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed", violations, request);
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception exception, HttpServletRequest request) {
        LOGGER.warn("Malformed request for {} {}", request.getMethod(), request.getRequestURI());
        return error(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST, "Request is malformed", List.of(), request);
    }

    @ExceptionHandler(AuditLogException.class)
    public ResponseEntity<ApiErrorResponse> handleAuditLogException(AuditLogException exception, HttpServletRequest request) {
        return error(statusFor(exception.getErrorCode()), exception.getErrorCode(), exception.getMessage(), List.of(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handlePersistenceConflict(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        LOGGER.warn("Persistence conflict while processing {} {}", request.getMethod(), request.getRequestURI(), exception);
        return error(HttpStatus.CONFLICT, ErrorCode.PERSISTENCE_CONFLICT,
                "The request conflicts with persisted audit data", List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled error while processing {} {}", request.getMethod(), request.getRequestURI(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred", List.of(), request);
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            ErrorCode code,
            String message,
            List<ApiErrorResponse.FieldViolation> violations,
            HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                MDC.get("traceId"),
                status.value(),
                code.name(),
                message,
                violations);
        return ResponseEntity.status(status).body(body);
    }

    private ApiErrorResponse.FieldViolation toFieldViolation(FieldError error) {
        return new ApiErrorResponse.FieldViolation(error.getField(), error.getDefaultMessage());
    }

    private HttpStatus statusFor(ErrorCode errorCode) {
        return switch (errorCode) {
            case AUDIT_EVENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CHAIN_INTEGRITY_VIOLATION, PERSISTENCE_CONFLICT -> HttpStatus.CONFLICT;
            case VALIDATION_FAILED, MALFORMED_REQUEST, INVALID_CURSOR -> HttpStatus.BAD_REQUEST;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
