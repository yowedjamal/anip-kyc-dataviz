package com.anip.kyc.exception;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String message) { super(message); }
    public SessionNotFoundException(String message, Throwable t) { super(message, t); }
}
