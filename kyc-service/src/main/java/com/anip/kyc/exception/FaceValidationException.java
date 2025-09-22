package com.anip.kyc.exception;

public class FaceValidationException extends RuntimeException {
    public FaceValidationException() { super(); }
    public FaceValidationException(String message) { super(message); }
    public FaceValidationException(String message, Throwable cause) { super(message, cause); }
    public FaceValidationException(Throwable cause) { super(cause); }
}
