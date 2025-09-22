package com.anip.kyc.exception;

public class FaceRecognitionException extends RuntimeException {
    public FaceRecognitionException(String message) { super(message); }
    public FaceRecognitionException(String message, Throwable t) { super(message, t); }
}
