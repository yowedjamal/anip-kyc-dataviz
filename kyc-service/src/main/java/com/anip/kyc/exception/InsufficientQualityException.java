package com.anip.kyc.exception;

public class InsufficientQualityException extends RuntimeException {
    public InsufficientQualityException() { super(); }
    public InsufficientQualityException(String message) { super(message); }
    public InsufficientQualityException(String message, Throwable cause) { super(message, cause); }
    public InsufficientQualityException(Throwable cause) { super(cause); }
}
