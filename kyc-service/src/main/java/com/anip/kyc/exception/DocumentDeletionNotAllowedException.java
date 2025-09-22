package com.anip.kyc.exception;

public class DocumentDeletionNotAllowedException extends RuntimeException {
    public DocumentDeletionNotAllowedException(String m){ super(m); }
}
