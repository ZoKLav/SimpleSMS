package com.android.messaging.storage;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** A Keystore wrapping key protects the random data key. No plaintext fallback. */
final class VaultKey {
    private final Context context;
    private final String alias;
    private SecretKey cached;
    VaultKey(Context context, String alias) { this.context = context; this.alias = alias; }
    synchronized SecretKey get(boolean hasRecords) throws Exception {
        if (cached != null) return cached;
        AtomicFile file = new AtomicFile(new File(context.getNoBackupFilesDir(), alias + ".wrapped"));
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        SecretKey wrapping = (SecretKey) ks.getKey(alias, null);
        boolean exists = file.getBaseFile().exists()
                || new File(file.getBaseFile().getPath() + ".bak").exists();
        if (wrapping == null && (exists || hasRecords))
            throw new IllegalStateException("The encryption key is unavailable");
        if (wrapping == null) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).build());
            wrapping = generator.generateKey();
        }
        byte[] raw;
        if (exists) {
            raw = AesGcm.decrypt(wrapping, file.readFully(), "SimpleSMS/data-key/v1/" + alias);
        } else {
            if (hasRecords) throw new IllegalStateException("The encrypted data key is unavailable");
            raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            byte[] envelope = AesGcm.encrypt(wrapping, raw, "SimpleSMS/data-key/v1/" + alias);
            FileOutputStream out = null;
            try { out = file.startWrite(); out.write(envelope); file.finishWrite(out); }
            catch (Exception e) { if (out != null) file.failWrite(out); Arrays.fill(raw, (byte) 0); throw e; }
        }
        try {
            if (raw.length != 32) throw new IllegalStateException("Invalid data key");
            cached = new SecretKeySpec(raw, "AES");
            return cached;
        } finally { Arrays.fill(raw, (byte) 0); }
    }
}
