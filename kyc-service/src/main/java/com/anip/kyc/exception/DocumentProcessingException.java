package com.anip.kyc.exception;

public class DocumentProcessingException extends RuntimeException {
    public DocumentProcessingException(String m){ super(m); }
    public DocumentProcessingException(String m, Throwable t){ super(m,t); }
}
