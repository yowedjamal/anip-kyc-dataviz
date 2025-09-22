package com.anip.kyc.exception;

public class BiometricDeletionNotAllowedException extends RuntimeException {
    public BiometricDeletionNotAllowedException() { super(); }
    public BiometricDeletionNotAllowedException(String message) { super(message); }
    public BiometricDeletionNotAllowedException(String message, Throwable cause) { super(message, cause); }
    public BiometricDeletionNotAllowedException(Throwable cause) { super(cause); }
}
