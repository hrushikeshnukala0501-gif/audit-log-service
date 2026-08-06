package com.auditlog.support.exception;

public class AuditLogException extends RuntimeException {

    private final ErrorCode errorCode;

    public AuditLogException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
