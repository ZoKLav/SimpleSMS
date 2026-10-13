package com.android.messaging.util;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import com.android.messaging.R;
import com.android.messaging.ui.conversation.ConversationActivity;

public final class NotificationHelper {
    private static final String PREFIX = "simplesms_";
    private NotificationHelper() { }
    public static void incoming(Context c, String address, long thread, String body) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        if (!AppPrefs.notifications(c) || AppPrefs.muted(c, address)) return;
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        if (manager == null) return;
        boolean preview = AppPrefs.preview(c), vibrate = AppPrefs.vibrate(c);
        Uri sound = AppPrefs.tone(c, address);
        String channel = PREFIX + Integer.toHexString((address + "|" + sound + "|" + vibrate + "|" + preview).hashCode());
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(channel, preview ? "Messages from " + ContactResolver.name(c, address)
                    : "SMS alerts", NotificationManager.IMPORTANCE_DEFAULT);
            ch.enableVibration(vibrate);
            ch.setSound(sound, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            ch.setLockscreenVisibility(preview ? Notification.VISIBILITY_PRIVATE : Notification.VISIBILITY_SECRET);
            manager.createNotificationChannel(ch);
        }
        Intent open = new Intent(c, ConversationActivity.class).putExtra(ConversationActivity.EXTRA_THREAD_ID, thread);
        PendingIntent pending = PendingIntent.getActivity(c, id(thread), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, channel) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notification).setContentTitle(preview ? ContactResolver.name(c, address) : "SimpleSMS")
                .setContentText(preview ? body : "New message").setContentIntent(pending).setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE).setVisibility(preview ? Notification.VISIBILITY_PRIVATE : Notification.VISIBILITY_SECRET);
        if (Build.VERSION.SDK_INT < 26) { b.setSound(sound); if (vibrate) b.setVibrate(new long[]{0, 180, 120, 180}); }
        manager.notify(id(thread), b.build());
    }
    public static void storageError(Context c) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        if (manager == null) return;
        String channel = PREFIX + "storage_error";
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(channel, "SMS storage errors", NotificationManager.IMPORTANCE_HIGH));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, channel) : new Notification.Builder(c);
        manager.notify(0x534d53, b.setSmallIcon(R.drawable.ic_notification).setContentTitle("SimpleSMS storage error")
                .setContentText("A message or status could not be saved. Open SimpleSMS to check storage.")
                .setVisibility(Notification.VISIBILITY_SECRET).build());
    }
    public static void cancel(Context c, long thread) {
        NotificationManager n = c.getSystemService(NotificationManager.class); if (n != null) n.cancel(id(thread));
    }
    public static void clearChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager n = c.getSystemService(NotificationManager.class);
        if (n != null) for (NotificationChannel ch : n.getNotificationChannels())
            if (ch.getId().startsWith(PREFIX)) n.deleteNotificationChannel(ch.getId());
    }
    private static int id(long thread) { return (int) ((thread ^ (thread >>> 32)) & 0x7fffffff); }
}
