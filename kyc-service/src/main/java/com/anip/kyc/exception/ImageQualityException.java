package com.anip.kyc.exception;

public class ImageQualityException extends RuntimeException {
    public ImageQualityException() { super(); }
    public ImageQualityException(String message) { super(message); }
    public ImageQualityException(String message, Throwable cause) { super(message, cause); }
    public ImageQualityException(Throwable cause) { super(cause); }
}
