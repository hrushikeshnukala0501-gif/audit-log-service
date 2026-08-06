package com.auditlog.application.service;

public class PayloadProtectionException extends RuntimeException {

    public PayloadProtectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
