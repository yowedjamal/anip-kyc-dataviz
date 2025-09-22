package com.anip.kyc.exception;

public class MultipleFacesDetectedException extends RuntimeException {
    public MultipleFacesDetectedException() { super(); }
    public MultipleFacesDetectedException(String message) { super(message); }
    public MultipleFacesDetectedException(String message, Throwable cause) { super(message, cause); }
    public MultipleFacesDetectedException(Throwable cause) { super(cause); }
}
