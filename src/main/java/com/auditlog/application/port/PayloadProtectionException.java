package com.auditlog.application.port;

/**
 * Indicates that a configured payload-protection provider could not protect or recover a payload.
 */
public class PayloadProtectionException extends RuntimeException {

    public PayloadProtectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
