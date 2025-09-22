package com.anip.kyc.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM implementation of EncryptionService.
 * Expects a Base64-encoded 32-byte key in property `app.encryption.key` when enabled.
 * Use with caution: key management must be handled securely in production (Vault/KMS).
 */
@Service
@ConditionalOnProperty(name = "app.encryption.enabled", havingValue = "true")
public class AesGcmEncryptionService implements EncryptionService {

    private static final String AES_ALGO = "AES";
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 16; // bytes
    private static final int GCM_IV_LENGTH = 12; // bytes

    @Value("${app.encryption.key:}")
    private String base64Key;

    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("app.encryption.key must be provided when app.encryption.enabled=true");
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("app.encryption.key must be 32 bytes (Base64 of 32 bytes) for AES-256");
        }
        this.secretKey = new SecretKeySpec(keyBytes, AES_ALGO);
    }

    @Override
    public String encrypt(String plain) {
        if (plain == null) return null;
        byte[] encrypted = encryptBytes(plain.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    @Override
    public String decrypt(String cipher) {
        if (cipher == null) return null;
        byte[] decoded = Base64.getDecoder().decode(cipher);
        byte[] plain = decryptBytes(decoded);
        return new String(plain, StandardCharsets.UTF_8);
    }

    @Override
    public byte[] encryptBytes(byte[] plain) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
            byte[] ciphertext = cipher.doFinal(plain);

            // Prepend IV length + IV + ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(4 + iv.length + ciphertext.length);
            buffer.putInt(iv.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return buffer.array();
        } catch (Exception e) {
            throw new RuntimeException("AES encryption failed", e);
        }
    }

    @Override
    public byte[] decryptBytes(byte[] cipher) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(cipher);
            int ivLength = buffer.getInt();
            if (ivLength != GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Invalid IV length in cipher text");
            }
            byte[] iv = new byte[ivLength];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher dec = Cipher.getInstance(AES_GCM_NO_PADDING);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            dec.init(Cipher.DECRYPT_MODE, secretKey, spec);
            return dec.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("AES decryption failed", e);
        }
    }
}
