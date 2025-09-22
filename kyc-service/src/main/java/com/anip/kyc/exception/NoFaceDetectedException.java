package com.anip.kyc.exception;

public class NoFaceDetectedException extends RuntimeException {
    public NoFaceDetectedException() { super(); }
    public NoFaceDetectedException(String message) { super(message); }
    public NoFaceDetectedException(String message, Throwable cause) { super(message, cause); }
    public NoFaceDetectedException(Throwable cause) { super(cause); }
}
