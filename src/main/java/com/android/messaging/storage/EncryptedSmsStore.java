package com.android.messaging.storage;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.LruCache;
import com.android.messaging.sms.PartTracker;
import com.android.messaging.sms.SmsRepository;
import com.android.messaging.sms.SmsRepository.Message;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONObject;

/** Only ciphertext and routing indexes enter SQLite, including its journal. */
public final class EncryptedSmsStore extends SQLiteOpenHelper {
    public static final long THREAD_OFFSET = 1L << 48;
    private static EncryptedSmsStore instance;
    private final Context context;
    private final VaultKey vaultKey;
    private final LruCache<String, Long> threadCache = new LruCache<>(512);
    private boolean migratedDrafts;
    public static synchronized EncryptedSmsStore get(Context c) {
        if (instance == null) instance = new EncryptedSmsStore(c.getApplicationContext(),
                "private-sms.db", "simplesms.message-key.v1");
        return instance;
    }
    // Package-private for isolated instrumented tests; the normal app uses get().
    EncryptedSmsStore(Context c, String database, String alias) {
        super(c, database, null, 1);
        context = c; vaultKey = new VaultKey(c, alias);
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "thread_id INTEGER NOT NULL, system_id INTEGER UNIQUE, token TEXT NOT NULL UNIQUE, payload BLOB NOT NULL)");
        db.execSQL("CREATE INDEX messages_thread ON messages(thread_id)");
        db.execSQL("CREATE TABLE drafts (name TEXT PRIMARY KEY, payload BLOB NOT NULL)");
        db.execSQL("CREATE TABLE threads (_id INTEGER PRIMARY KEY AUTOINCREMENT, lookup TEXT UNIQUE NOT NULL, payload BLOB NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported encrypted database version");
    }
    private SecretKey key() throws Exception {
        boolean records;
        try (Cursor x = getReadableDatabase().rawQuery("SELECT EXISTS(SELECT 1 FROM messages) "
                + "OR EXISTS(SELECT 1 FROM threads) OR EXISTS(SELECT 1 FROM drafts)", null)) {
            x.moveToFirst(); records = x.getInt(0) != 0;
        }
        return vaultKey.get(records);
    }
    private byte[] encrypt(String value, String binding) {
        try { return AesGcm.encrypt(key(), value.getBytes(StandardCharsets.UTF_8), binding); }
        catch (Exception e) { throw new IllegalStateException("Encrypted storage write failed", e); }
    }
    private String decrypt(byte[] payload, String binding) {
        try { return new String(AesGcm.decrypt(key(), payload, binding), StandardCharsets.UTF_8); }
        catch (Exception e) { throw new IllegalStateException("Encrypted storage could not be authenticated", e); }
    }
    private static String binding(long row, long thread, long source) {
        return "message:" + row + ":" + thread + ":" + source;
    }
    private String normalize(String address) {
        String raw = address.trim();
        if (!raw.matches("[+0-9(). \\-]+")) return raw.toLowerCase(Locale.ROOT);
        String number = PhoneNumberUtils.normalizeNumber(raw);
        String region = Locale.getDefault().getCountry();
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null && !tm.getSimCountryIso().isEmpty()) region = tm.getSimCountryIso();
        } catch (SecurityException ignored) { }
        String e164 = PhoneNumberUtils.formatNumberToE164(number, region.toUpperCase(Locale.ROOT));
        return e164 == null ? number : e164;
    }
    public synchronized long threadFor(String address) {
        if (address == null || address.trim().isEmpty()) throw new IllegalArgumentException("Missing address");
        String normalized = normalize(address);
        Long cached = threadCache.get(normalized);
        if (cached != null) return cached;
        String lookup;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key().getEncoded(), "HmacSHA256"));
            byte[] digest = mac.doFinal(("thread/v1/" + normalized).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
            lookup = hex.toString();
        } catch (Exception e) { throw new IllegalStateException("Could not resolve encrypted thread", e); }
        SQLiteDatabase db = getWritableDatabase();
        try (Cursor x = db.query("threads", new String[]{"_id", "payload"}, "lookup=?",
                new String[]{lookup}, null, null, null)) {
            if (x.moveToFirst()) {
                long row = x.getLong(0);
                String stored = decrypt(x.getBlob(1), "thread:" + row + ":" + lookup);
                if (!stored.equals(normalized)) throw new IllegalStateException("Thread binding mismatch");
                long id = THREAD_OFFSET + row; threadCache.put(normalized, id); return id;
            }
        }
        boolean own = !db.inTransaction();
        if (own) db.beginTransaction();
        try {
            ContentValues v = new ContentValues(); v.put("lookup", lookup); v.put("payload", new byte[0]);
            long row = db.insertOrThrow("threads", null, v);
            v.clear(); v.put("payload", encrypt(normalized, "thread:" + row + ":" + lookup));
            db.update("threads", v, "_id=?", new String[]{Long.toString(row)});
            if (own) db.setTransactionSuccessful();
            long id = THREAD_OFFSET + row; threadCache.put(normalized, id); return id;
        } finally { if (own) { db.endTransaction(); threadCache.evictAll(); } }
    }
    private Message read(Cursor x) {
        long row = x.getLong(0), thread = x.getLong(1), source = x.isNull(2) ? 0 : x.getLong(2);
        try {
            Message m = Message.from(new JSONObject(decrypt(x.getBlob(4), binding(row, thread, source))));
            if (!m.token.equals(x.getString(3))) throw new IllegalStateException("Message binding mismatch");
            m.id = -row; m.threadId = thread; m.systemId = source; m.encrypted = true; return m;
        } catch (Exception e) { throw new IllegalStateException("Could not read encrypted message", e); }
    }
    private Message find(String column, String value) {
        try (Cursor x = getReadableDatabase().query("messages",
                new String[]{"_id", "thread_id", "system_id", "token", "payload"},
                column + "=?", new String[]{value}, null, null, null)) {
            return x.moveToFirst() ? read(x) : null;
        }
    }
    public synchronized Message message(long id) {
        if (id >= 0) throw new IllegalArgumentException("Not a private message ID");
        return find("_id", Long.toString(-id));
    }
    public synchronized List<Message> messages(long thread) {
        migrateDrafts();
        ArrayList<Message> out = new ArrayList<>();
        try (Cursor x = getReadableDatabase().query("messages",
                new String[]{"_id", "thread_id", "system_id", "token", "payload"},
                thread == 0 ? null : "thread_id=?", thread == 0 ? null : new String[]{Long.toString(thread)},
                null, null, "_id ASC")) {
            while (x.moveToNext()) out.add(read(x));
        }
        return out;
    }
    public synchronized long insert(Message m) {
        try { Message.from(m.json()); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid message", e); }
        SQLiteDatabase db = getWritableDatabase();
        boolean own = !db.inTransaction();
        if (own) db.beginTransaction();
        try {
            Message duplicate = find("token", m.token);
            if (duplicate != null) {
                if (!SmsRepository.sameOriginal(duplicate, m)) throw new IllegalStateException("Conflicting message token");
                if (own) db.setTransactionSuccessful(); return duplicate.id;
            }
            if (m.systemId > 0) {
                Message copy = find("system_id", Long.toString(m.systemId));
                if (copy != null) {
                    if (SmsRepository.sameOriginal(copy, m)) {
                        refreshSystemCopy(copy, m);
                        if (own) db.setTransactionSuccessful(); return copy.id;
                    }
                    // A platform backup can reuse a row ID. Preserve both histories.
                    detachSystemCopy(copy.id);
                }
            }
            m.threadId = threadFor(m.address);
            ContentValues v = new ContentValues(); v.put("thread_id", m.threadId);
            if (m.systemId > 0) v.put("system_id", m.systemId); else v.putNull("system_id");
            v.put("token", m.token); v.put("payload", new byte[0]);
            long row = db.insertOrThrow("messages", null, v);
            m.id = -row; m.encrypted = true; write(m);
            if (own) db.setTransactionSuccessful(); return m.id;
        } finally { if (own) { db.endTransaction(); threadCache.evictAll(); } }
    }
    private void write(Message m) {
        try {
            ContentValues v = new ContentValues();
            v.put("payload", encrypt(m.json().toString(), binding(-m.id, m.threadId, m.systemId)));
            if (m.systemId > 0) v.put("system_id", m.systemId); else v.putNull("system_id");
            int updated = getWritableDatabase().update("messages", v, "_id=? AND token=?",
                    new String[]{Long.toString(-m.id), m.token});
            if (updated != 1) throw new IllegalStateException("Encrypted message disappeared");
        } catch (Exception e) { throw new IllegalStateException("Could not save encrypted message", e); }
    }
    public synchronized void refreshSystemCopy(Message copy, Message source) {
        Message current = message(copy.id);
        if (current == null || current.systemId != source.systemId
                || !SmsRepository.sameOriginal(current, source)) return;
        boolean read = current.read || source.read;
        long sentTime = source.dateSent > 0 ? source.dateSent : current.dateSent;
        if (current.type != source.type || current.dateSent != sentTime || current.status != source.status
                || current.error != source.error || current.read != read) {
            current.type = source.type; current.dateSent = sentTime; current.status = source.status;
            current.error = source.error; current.read = read; write(current);
        }
        copy.type = current.type; copy.dateSent = current.dateSent; copy.status = current.status;
        copy.error = current.error; copy.read = current.read;
    }
    public synchronized void detachSystemCopy(long id) {
        Message m = message(id); if (m == null || m.systemId == 0) return;
        m.systemId = 0; write(m);
    }
    public synchronized void markRead(long thread) {
        migrateDrafts();
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            for (Message m : messages(thread)) if (!m.read) { m.read = true; write(m); }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public synchronized void sentPart(long id, int part, boolean success, int error, long when) {
        Message m = message(id); if (m == null) return;
        PartTracker.record(m.sentParts, part, success);
        if (PartTracker.failed(m.sentParts)) {
            m.type = Telephony.Sms.MESSAGE_TYPE_FAILED;
            if (!success) m.error = error;
        }
        else if (PartTracker.complete(m.sentParts)) {
            m.type = Telephony.Sms.MESSAGE_TYPE_SENT; m.error = 0;
            if (m.dateSent == 0) m.dateSent = when;
        }
        write(m);
    }
    public synchronized void deliveryPart(long id, int part, boolean success, long when) {
        Message m = message(id); if (m == null) return;
        PartTracker.record(m.deliveryParts, part, success);
        if (PartTracker.failed(m.deliveryParts)) m.status = Telephony.Sms.STATUS_FAILED;
        else if (PartTracker.complete(m.deliveryParts)) {
            m.status = Telephony.Sms.STATUS_COMPLETE;
            if (m.dateDelivered == 0) m.dateDelivered = when;
        }
        write(m);
    }
    public synchronized void failSend(long id, int code) {
        Message m = message(id); if (m == null) return;
        m.type = Telephony.Sms.MESSAGE_TYPE_FAILED; m.error = code;
        for (int i = 0; i < m.sentParts.length; i++) if (m.sentParts[i] == 0) m.sentParts[i] = 2;
        write(m);
    }
    public synchronized void deleteMessage(long id) {
        getWritableDatabase().delete("messages", "_id=?", new String[]{Long.toString(-id)});
    }
    public synchronized void deleteThread(long thread) {
        getWritableDatabase().delete("messages", "thread_id=?", new String[]{Long.toString(thread)});
        putDraft("draft_" + thread, "");
        // Retain the encrypted mapping so future incoming SMS uses the same archive/color history.
    }
    public synchronized String draft(String name) {
        migrateDrafts();
        try (Cursor x = getReadableDatabase().query("drafts", new String[]{"payload"}, "name=?",
                new String[]{name}, null, null, null)) {
            return x.moveToFirst() ? decrypt(x.getBlob(0), "draft:" + name) : "";
        }
    }
    public synchronized void putDraft(String name, String body) {
        if (body == null || body.isEmpty()) {
            getWritableDatabase().delete("drafts", "name=?", new String[]{name}); return;
        }
        byte[] payload = encrypt(body, "draft:" + name);
        ContentValues v = new ContentValues(); v.put("name", name); v.put("payload", payload);
        getWritableDatabase().insertWithOnConflict("drafts", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }
    private void migrateDrafts() {
        if (migratedDrafts) return;
        SharedPreferences p = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = p.edit();
        for (Map.Entry<String, ?> entry : p.getAll().entrySet()) {
            if (!entry.getKey().startsWith("draft_") || !(entry.getValue() instanceof String)) continue;
            putDraft(entry.getKey(), (String) entry.getValue()); edit.remove(entry.getKey());
        }
        if (!edit.commit()) throw new IllegalStateException("Could not remove legacy plaintext drafts");
        migratedDrafts = true;
    }
    public synchronized int restore(List<Message> records) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction(); int added = 0;
        try {
            for (Message m : records) {
                if (find("token", m.token) != null) {
                    Message existing = find("token", m.token);
                    if (!SmsRepository.sameOriginal(existing, m)) throw new IllegalStateException("Conflicting backup message");
                    continue;
                }
                m.id = 0; m.systemId = 0; m.threadId = 0;
                if (m.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || m.type == Telephony.Sms.MESSAGE_TYPE_QUEUED) {
                    m.type = Telephony.Sms.MESSAGE_TYPE_FAILED; m.error = -1;
                    for (int i = 0; i < m.sentParts.length; i++) if (m.sentParts[i] == 0) m.sentParts[i] = 2;
                }
                insert(m); added++;
            }
            db.setTransactionSuccessful(); return added;
        } finally { db.endTransaction(); threadCache.evictAll(); }
    }
}
