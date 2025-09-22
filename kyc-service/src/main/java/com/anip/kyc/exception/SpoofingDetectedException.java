package com.anip.kyc.exception;

public class SpoofingDetectedException extends RuntimeException {
    public SpoofingDetectedException() { super(); }
    public SpoofingDetectedException(String message) { super(message); }
    public SpoofingDetectedException(String message, Throwable cause) { super(message, cause); }
    public SpoofingDetectedException(Throwable cause) { super(cause); }
}
