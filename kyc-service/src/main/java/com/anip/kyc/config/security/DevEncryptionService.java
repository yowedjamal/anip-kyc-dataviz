package com.anip.kyc.config.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Development implementation of {@link EncryptionService}.
 *
 * This implementation is intentionally simple and NOT SECURE: it uses Base64 encoding
 * as a reversible transformation. It's suitable for local development and tests only.
 * Replace with a proper crypto-backed implementation for production.
 */
@Service
@ConditionalOnProperty(name = "app.encryption.enabled", havingValue = "false", matchIfMissing = true)
public class DevEncryptionService implements EncryptionService {

    private final Base64.Encoder encoder = Base64.getEncoder();
    private final Base64.Decoder decoder = Base64.getDecoder();

    @Override
    public String encrypt(String plain) {
        if (plain == null) return null;
        return encoder.encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String decrypt(String cipher) {
        if (cipher == null) return null;
        try {
            byte[] decoded = decoder.decode(cipher);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // If input was not Base64, return as-is to be tolerant in mixed-data scenarios
            return cipher;
        }
    }

    @Override
    public byte[] encryptBytes(byte[] plain) {
        if (plain == null) return null;
        return encoder.encode(plain);
    }

    @Override
    public byte[] decryptBytes(byte[] cipher) {
        if (cipher == null) return null;
        try {
            return decoder.decode(cipher);
        } catch (IllegalArgumentException e) {
            return cipher;
        }
    }
}
