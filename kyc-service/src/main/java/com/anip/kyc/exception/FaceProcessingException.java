package com.anip.kyc.exception;

public class FaceProcessingException extends RuntimeException {
    public FaceProcessingException() { super(); }
    public FaceProcessingException(String message) { super(message); }
    public FaceProcessingException(String message, Throwable cause) { super(message, cause); }
    public FaceProcessingException(Throwable cause) { super(cause); }
}
