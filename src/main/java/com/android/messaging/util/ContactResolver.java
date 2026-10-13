package com.android.messaging.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.LruCache;
import java.util.ArrayList;
import java.util.List;

/** All lookups use the on-device contacts provider; grants and edits invalidate the cache. */
public final class ContactResolver {
    private static final LruCache<String, String> CACHE = new LruCache<>(256);
    private ContactResolver() { }
    public static boolean allowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }
    public static void invalidate() { CACHE.evictAll(); }
    public static String cachedName(Context c, String address) {
        if (address == null || address.trim().isEmpty()) return "Unknown";
        if (!allowed(c)) return address;
        String found = CACHE.get(address); return found == null ? address : found;
    }
    public static String name(Context c, String address) {
        if (address == null || address.trim().isEmpty()) return "Unknown";
        if (!allowed(c)) return address;
        String found = CACHE.get(address);
        if (found != null) return found;
        Uri uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon().appendPath(address).build();
        try (Cursor x = c.getContentResolver().query(uri,
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (x != null && x.moveToFirst()) {
                found = x.getString(0);
                if (found != null && !found.isEmpty()) { CACHE.put(address, found); return found; }
            }
        } catch (SecurityException e) { invalidate(); }
        CACHE.put(address, address); return address;
    }
    public static final class Recipient {
        public final String name, number;
        Recipient(String name, String number) { this.name = name; this.number = number; }
        @Override public String toString() { return name + " · " + number; }
    }
    public static List<Recipient> recipients(Context c) {
        ArrayList<Recipient> out = new ArrayList<>();
        if (!allowed(c)) return out;
        try (Cursor x = c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")) {
            if (x != null) while (x.moveToNext()) {
                String name = x.getString(0), number = x.getString(1);
                if (number != null && !number.isEmpty()) out.add(new Recipient(name == null ? number : name, number));
            }
        } catch (SecurityException e) { invalidate(); }
        return out;
    }
}
