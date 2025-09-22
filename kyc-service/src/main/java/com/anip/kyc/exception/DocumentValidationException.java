package com.anip.kyc.exception;

public class DocumentValidationException extends RuntimeException {
    public DocumentValidationException(String message) { super(message); }
    public DocumentValidationException(String message, Throwable t) { super(message, t); }
}
