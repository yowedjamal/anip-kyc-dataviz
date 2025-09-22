package com.anip.kyc.config.security;

/**
 * Minimal EncryptionService stub used by services for encrypt/decrypt operations.
 * In production this should be replaced by a real implementation wiring keys and secure primitives.
 */
public interface EncryptionService {
    String encrypt(String plain);
    String decrypt(String cipher);
    byte[] encryptBytes(byte[] plain);
    byte[] decryptBytes(byte[] cipher);
}
