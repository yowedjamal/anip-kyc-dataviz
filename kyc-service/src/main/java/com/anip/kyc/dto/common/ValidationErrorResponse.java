package com.anip.kyc.dto.common;

import java.util.List;

public class ValidationErrorResponse {
    private String field;
    private Object rejectedValue;
    private String message;

    public String getField() { return field; }
    public Object getRejectedValue() { return rejectedValue; }
    public String getMessage() { return message; }

    public void setField(String field) { this.field = field; }
    public void setRejectedValue(Object v) { this.rejectedValue = v; }
    public void setMessage(String message) { this.message = message; }

    public static Builder builder(){ return new Builder(); }
    public static class Builder {
        private final ValidationErrorResponse r = new ValidationErrorResponse();
        public Builder field(String f){ r.field = f; return this; }
        public Builder rejectedValue(Object v){ r.rejectedValue = v; return this; }
        public Builder message(String m){ r.message = m; return this; }
        public ValidationErrorResponse build(){ return r; }
    }
}
