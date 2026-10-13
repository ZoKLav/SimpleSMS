package com.android.messaging.sms;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import com.android.messaging.storage.EncryptedSmsStore;
import com.android.messaging.util.AppPrefs;
import com.android.messaging.util.ContactResolver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class SmsRepository {
    public static final class Conversation {
        public long threadId, messageId, date;
        public String address, title, snippet;
        public int type, unread;
    }
    public static final class Message {
        public long id, threadId, date, dateSent, dateDelivered, systemId;
        public String address = "", body = "", token = UUID.randomUUID().toString();
        public int type, status = Telephony.Sms.STATUS_NONE, error, subscriptionId = -1;
        public boolean read, encrypted;
        public int[] sentParts = new int[0], deliveryParts = new int[0];
        public JSONObject json() throws JSONException {
            return new JSONObject().put("token", token).put("address", address).put("body", body)
                    .put("date", date).put("dateSent", dateSent).put("dateDelivered", dateDelivered)
                    .put("type", type).put("status", status).put("error", error).put("read", read)
                    .put("subscriptionId", subscriptionId).put("sentParts", array(sentParts))
                    .put("deliveryParts", array(deliveryParts));
        }
        public static Message from(JSONObject object) throws JSONException {
            Message m = new Message();
            m.token = object.getString("token");
            UUID.fromString(m.token);
            m.address = object.getString("address"); m.body = object.getString("body");
            m.date = object.getLong("date"); m.dateSent = object.optLong("dateSent");
            m.dateDelivered = object.optLong("dateDelivered"); m.type = object.getInt("type");
            m.status = object.optInt("status", Telephony.Sms.STATUS_NONE); m.error = object.optInt("error");
            m.read = object.optBoolean("read"); m.subscriptionId = object.optInt("subscriptionId", -1);
            m.sentParts = parts(object.optJSONArray("sentParts"));
            m.deliveryParts = parts(object.optJSONArray("deliveryParts"));
            if (m.address.trim().isEmpty() || m.date <= 0 || m.dateSent < 0 || m.dateDelivered < 0
                    || m.type < Telephony.Sms.MESSAGE_TYPE_INBOX || m.type > Telephony.Sms.MESSAGE_TYPE_QUEUED)
                throw new JSONException("Invalid message record");
            return m;
        }
        private static JSONArray array(int[] parts) {
            JSONArray a = new JSONArray(); for (int part : parts) a.put(part); return a;
        }
        private static int[] parts(JSONArray a) throws JSONException {
            if (a == null) return new int[0];
            if (a.length() > 255) throw new JSONException("Too many SMS parts");
            int[] out = new int[a.length()];
            for (int i = 0; i < out.length; i++) {
                out[i] = a.getInt(i);
                if (out[i] < 0 || out[i] > 2) throw new JSONException("Invalid SMS part state");
            }
            return out;
        }
    }
    private static final CopyOnWriteArraySet<Runnable> LISTENERS = new CopyOnWriteArraySet<>();
    private SmsRepository() { }
    public static void listen(Runnable r) { LISTENERS.add(r); }
    public static void unlisten(Runnable r) { LISTENERS.remove(r); }
    public static void changed() {
        new Handler(Looper.getMainLooper()).post(() -> { for (Runnable r : LISTENERS) r.run(); });
    }
    public static boolean canReadSystem(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }
    /** ID reuse after a provider restore must never hide or delete a different message. */
    public static boolean sameOriginal(Message a, Message b) {
        return a.date == b.date && Objects.equals(a.address, b.address) && Objects.equals(a.body, b.body);
    }
    public static List<Message> systemMessages(Context c) {
        ArrayList<Message> out = new ArrayList<>();
        if (!canReadSystem(c)) return out;
        String[] projection = {Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                Telephony.Sms.DATE, Telephony.Sms.DATE_SENT, Telephony.Sms.TYPE, Telephony.Sms.STATUS,
                Telephony.Sms.ERROR_CODE, Telephony.Sms.READ, Telephony.Sms.SUBSCRIPTION_ID};
        try (Cursor x = c.getContentResolver().query(Telephony.Sms.CONTENT_URI,
                projection, Telephony.Sms.TYPE + "!=?", new String[]{Integer.toString(Telephony.Sms.MESSAGE_TYPE_DRAFT)}, Telephony.Sms.DATE + " ASC")) {
            if (x != null) while (x.moveToNext()) {
                Message m = new Message();
                m.id = m.systemId = x.getLong(0); m.address = x.getString(1); m.body = x.getString(2);
                if (m.address == null || m.address.trim().isEmpty()) m.address = "Unknown";
                if (m.body == null) m.body = "";
                m.date = x.getLong(3); m.dateSent = Math.max(0, x.getLong(4)); m.type = x.getInt(5);
                m.status = x.getInt(6); m.error = x.getInt(7); m.read = x.getInt(8) != 0;
                m.subscriptionId = x.getInt(9);
                m.threadId = EncryptedSmsStore.get(c).threadFor(m.address);
                out.add(m);
            }
        }
        return out;
    }
    /** thread=0 means all messages. Reads private history even with SMS permission denied. */
    public static List<Message> messages(Context c, long thread) {
        EncryptedSmsStore store = EncryptedSmsStore.get(c);
        List<Message> privateRows = store.messages(thread);
        HashMap<Long, Message> copied = new HashMap<>();
        for (Message m : privateRows) if (m.systemId > 0) copied.put(m.systemId, m);
        ArrayList<Message> out = new ArrayList<>(privateRows);
        for (Message source : systemMessages(c)) {
            if (thread != 0 && source.threadId != thread) continue;
            Message copy = copied.get(source.systemId);
            if (copy != null && sameOriginal(copy, source)) store.refreshSystemCopy(copy, source);
            else out.add(source);
        }
        Collections.sort(out, (a, b) -> {
            int cmp = Long.compare(a.date, b.date);
            return cmp != 0 ? cmp : Long.compare(a.id, b.id);
        });
        return out;
    }
    public static List<Conversation> conversations(Context c, boolean archived) {
        LinkedHashMap<Long, Conversation> map = new LinkedHashMap<>();
        List<Message> all = messages(c, 0);
        for (int i = all.size() - 1; i >= 0; i--) {
            Message m = all.get(i);
            Conversation q = map.get(m.threadId);
            if (q == null) {
                q = new Conversation(); q.threadId = m.threadId; q.messageId = m.id;
                q.address = m.address; q.snippet = m.body; q.date = m.date; q.type = m.type;
                map.put(q.threadId, q);
            }
            if (m.type == Telephony.Sms.MESSAGE_TYPE_INBOX && !m.read) q.unread++;
        }
        ArrayList<Conversation> out = new ArrayList<>();
        for (Conversation q : map.values()) if (AppPrefs.isArchived(c, q.threadId) == archived) {
            q.title = ContactResolver.name(c, q.address); out.add(q);
        }
        return out;
    }
    public static void markRead(Context c, long thread) {
        markRead(c, thread, true);
    }
    public static void markRead(Context c, long thread, boolean notify) {
        EncryptedSmsStore.get(c).markRead(thread);
        if (canReadSystem(c) && SmsRole.isDefault(c)) {
            ContentValues v = new ContentValues(); v.put(Telephony.Sms.READ, 1); v.put(Telephony.Sms.SEEN, 1);
            for (Message m : systemMessages(c)) if (m.threadId == thread && !m.read)
                c.getContentResolver().update(systemUri(m.systemId), v, originalWhere(), originalArgs(m));
        }
        if (notify) changed();
    }
    public static void deleteThread(Context c, long thread) {
        if (canReadSystem(c)) {
            for (Message m : systemMessages(c)) if (m.threadId == thread) {
                if (!SmsRole.isDefault(c)) throw new IllegalStateException("Set SimpleSMS as the default SMS app first");
                c.getContentResolver().delete(systemUri(m.systemId), originalWhere(), originalArgs(m));
            }
        }
        EncryptedSmsStore.get(c).deleteThread(thread);
        AppPrefs.archive(c, thread, false); changed();
    }
    public static void deleteMessage(Context c, Message expected) {
        Message m = expected;
        if (expected.id < 0) {
            m = EncryptedSmsStore.get(c).message(expected.id);
            if (m == null) return;
            if (!sameOriginal(m, expected)) throw new IllegalStateException("Message changed before deletion");
        }
        if (m.systemId > 0) {
            if (!SmsRole.isDefault(c)) throw new IllegalStateException("Set SimpleSMS as the default SMS app first");
            c.getContentResolver().delete(systemUri(m.systemId), originalWhere(), originalArgs(m));
        }
        if (expected.id < 0) EncryptedSmsStore.get(c).deleteMessage(expected.id);
        changed();
    }
    public static Uri systemUri(long id) { return Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, Long.toString(id)); }
    public static String originalWhere() { return Telephony.Sms.DATE + "=? AND " + Telephony.Sms.ADDRESS + "=? AND " + Telephony.Sms.BODY + "=?"; }
    public static String[] originalArgs(Message m) { return new String[]{Long.toString(m.date), m.address, m.body}; }
}
