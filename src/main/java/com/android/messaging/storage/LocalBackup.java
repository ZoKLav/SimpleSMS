package com.android.messaging.storage;

import android.content.Context;
import android.net.Uri;
import com.android.messaging.sms.SmsRepository;
import com.android.messaging.sms.SmsRepository.Message;
import com.android.messaging.sms.SmsRole;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LocalBackup {
    private LocalBackup() { }
    public static void requireLocal(Uri uri) {
        String a = uri == null ? null : uri.getAuthority();
        if (uri == null || !"content".equals(uri.getScheme())
                || !("com.android.externalstorage.documents".equals(a)
                || "com.android.providers.downloads.documents".equals(a)
                || "com.android.providers.media.documents".equals(a)))
            throw new IllegalArgumentException("Choose a folder or file on this device");
    }
    public static int export(Context c, Uri uri, char[] password) throws Exception {
        requireLocal(uri);
        SmsRepository.messages(c, 0); // Refresh final sent timestamps on previously copied system rows.
        List<Message> messages = EncryptedSmsStore.get(c).messages(0);
        JSONArray rows = new JSONArray(); for (Message m : messages) rows.put(m.json());
        byte[] plain = new JSONObject().put("version", 1).put("messages", rows)
                .toString().getBytes(StandardCharsets.UTF_8);
        byte[] encrypted;
        try { encrypted = BackupCipher.encrypt(plain, password); }
        finally { Arrays.fill(plain, (byte) 0); }
        try (OutputStream out = c.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IllegalStateException("Could not open backup file");
            out.write(encrypted); out.flush();
        } finally { Arrays.fill(encrypted, (byte) 0); }
        return messages.size();
    }
    public static int restore(Context c, Uri uri, char[] password) throws Exception {
        requireLocal(uri);
        if (!SmsRole.isDefault(c)) throw new IllegalStateException("Set SimpleSMS as the default SMS app first");
        byte[] file;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("Could not open backup file");
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > BackupCipher.MAX_BYTES) throw new IllegalArgumentException("Backup exceeds 64 MiB");
                out.write(buffer, 0, n);
            }
            file = out.toByteArray();
        }
        byte[] plain;
        try { plain = BackupCipher.decrypt(file, password); }
        finally { Arrays.fill(file, (byte) 0); }
        ArrayList<Message> messages = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(new String(plain, StandardCharsets.UTF_8));
            if (root.getInt("version") != 1) throw new IllegalArgumentException("Unsupported backup version");
            JSONArray rows = root.getJSONArray("messages");
            for (int i = 0; i < rows.length(); i++) messages.add(Message.from(rows.getJSONObject(i)));
        } finally { Arrays.fill(plain, (byte) 0); }
        int added = EncryptedSmsStore.get(c).restore(messages);
        SmsRepository.changed(); return added;
    }
    /** Copy and authenticate each record before removing its exact shared-provider original. */
    public static int importSystem(Context c, boolean move) {
        if (!SmsRepository.canReadSystem(c)) throw new IllegalStateException("Grant SMS access first");
        if (move && !SmsRole.isDefault(c)) throw new IllegalStateException("Set SimpleSMS as the default SMS app first");
        EncryptedSmsStore store = EncryptedSmsStore.get(c);
        List<Message> sources = SmsRepository.systemMessages(c); int count = 0;
        for (Message source : sources) {
            long id = store.insert(source);
            Message verified = store.message(id);
            if (verified == null || !SmsRepository.sameOriginal(verified, source))
                throw new IllegalStateException("Encrypted copy could not be verified");
            if (move) {
                int removed = c.getContentResolver().delete(SmsRepository.systemUri(source.systemId),
                        SmsRepository.originalWhere(), SmsRepository.originalArgs(source));
                if (removed != 1) throw new IllegalStateException("An original changed during the move; its encrypted copy was kept");
                store.detachSystemCopy(id);
            }
            count++;
        }
        SmsRepository.changed(); return count;
    }
}
