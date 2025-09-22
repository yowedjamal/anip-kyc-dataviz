package com.anip.kyc.exception;

public class ValidationException extends RuntimeException {
    private String field;
    private Object rejectedValue;

    public ValidationException(String message, String field, Object rejectedValue) {
        super(message);
        this.field = field;
        this.rejectedValue = rejectedValue;
    }

    public String getField(){ return field; }
    public Object getRejectedValue(){ return rejectedValue; }
}
