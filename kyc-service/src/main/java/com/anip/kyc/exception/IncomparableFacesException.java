package com.anip.kyc.exception;

public class IncomparableFacesException extends RuntimeException {
    public IncomparableFacesException() { super(); }
    public IncomparableFacesException(String message) { super(message); }
    public IncomparableFacesException(String message, Throwable cause) { super(message, cause); }
    public IncomparableFacesException(Throwable cause) { super(cause); }
}
