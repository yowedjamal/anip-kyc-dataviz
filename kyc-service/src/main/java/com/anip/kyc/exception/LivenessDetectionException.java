package com.anip.kyc.exception;

public class LivenessDetectionException extends RuntimeException {
    public LivenessDetectionException() { super(); }
    public LivenessDetectionException(String message) { super(message); }
    public LivenessDetectionException(String message, Throwable cause) { super(message, cause); }
    public LivenessDetectionException(Throwable cause) { super(cause); }
}
