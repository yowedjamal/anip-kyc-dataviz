package com.anip.kyc.exception;

public class BiometricProcessingException extends RuntimeException {
    public BiometricProcessingException() { super(); }
    public BiometricProcessingException(String message) { super(message); }
    public BiometricProcessingException(String message, Throwable cause) { super(message, cause); }
    public BiometricProcessingException(Throwable cause) { super(cause); }
}
