package com.android.messaging.storage;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Versioned AES-GCM envelope. The provider generates a new nonce for every write. */
public final class AesGcm {
    private AesGcm() { }
    public static byte[] encrypt(SecretKey key, byte[] plain, String binding)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        if (iv.length != 12) throw new GeneralSecurityException("Unexpected nonce length");
        byte[] encrypted = cipher.doFinal(plain);
        byte[] out = new byte[13 + encrypted.length];
        out[0] = 1;
        System.arraycopy(iv, 0, out, 1, 12);
        System.arraycopy(encrypted, 0, out, 13, encrypted.length);
        return out;
    }
    public static byte[] decrypt(SecretKey key, byte[] envelope, String binding)
            throws GeneralSecurityException {
        if (envelope == null || envelope.length < 29 || envelope[0] != 1)
            throw new GeneralSecurityException("Invalid encrypted record");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(128, Arrays.copyOfRange(envelope, 1, 13)));
        cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(envelope, 13, envelope.length - 13);
    }
}
