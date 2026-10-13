package com.android.messaging.storage;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Portable backup: PBKDF2 password key, random salt, authenticated AES-GCM. */
public final class BackupCipher {
    public static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final byte[] MAGIC = {'S', 'S', 'M', 'S', 1};
    private static final int ROUNDS = 1_300_000, HEADER = 25;
    private BackupCipher() { }
    private static SecretKey derive(char[] password, byte[] salt) throws GeneralSecurityException {
        if (password == null || password.length < 12)
            throw new GeneralSecurityException("Use a backup password of at least 12 characters");
        // SHA-1 PBKDF2 is available on every supported Android version (API 24+).
        PBEKeySpec spec = new PBEKeySpec(password, salt, ROUNDS, 256);
        byte[] raw = null;
        try {
            raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
            return new SecretKeySpec(raw, "AES");
        } finally { spec.clearPassword(); if (raw != null) Arrays.fill(raw, (byte) 0); }
    }
    private static String binding(byte[] header) {
        StringBuilder hex = new StringBuilder("SimpleSMS/backup/v1/");
        for (byte b : header) { hex.append(Character.forDigit((b & 255) >>> 4, 16)); hex.append(Character.forDigit(b & 15, 16)); }
        return hex.toString();
    }
    public static byte[] encrypt(byte[] plain, char[] password) throws GeneralSecurityException {
        if (plain == null || plain.length > MAX_BYTES - HEADER - 29)
            throw new GeneralSecurityException("Backup exceeds 64 MiB");
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        byte[] header = ByteBuffer.allocate(HEADER).put(MAGIC).putInt(ROUNDS).put(salt).array();
        byte[] encrypted = AesGcm.encrypt(derive(password, salt), plain, binding(header));
        return ByteBuffer.allocate(header.length + encrypted.length).put(header).put(encrypted).array();
    }
    public static byte[] decrypt(byte[] file, char[] password) throws GeneralSecurityException {
        if (file == null || file.length < HEADER + 29 || file.length > MAX_BYTES)
            throw new GeneralSecurityException("Invalid encrypted backup");
        ByteBuffer input = ByteBuffer.wrap(file);
        byte[] magic = new byte[5]; input.get(magic);
        if (!Arrays.equals(magic, MAGIC) || input.getInt() != ROUNDS)
            throw new GeneralSecurityException("Unsupported backup format");
        byte[] salt = new byte[16]; input.get(salt);
        return AesGcm.decrypt(derive(password, salt), Arrays.copyOfRange(file, HEADER, file.length),
                binding(Arrays.copyOf(file, HEADER)));
    }
}
