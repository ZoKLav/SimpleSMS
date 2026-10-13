package com.android.messaging.sms;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import com.android.messaging.storage.EncryptedSmsStore;
import com.android.messaging.util.AppPrefs;
import java.util.ArrayList;

public final class SmsSender {
    public static final String ID = "message_id", PART = "part";
    private SmsSender() { }
    public static String sentAction(Context c) { return c.getPackageName() + ".SENT"; }
    public static String deliveryAction(Context c) { return c.getPackageName() + ".DELIVERED"; }
    public static long send(Context c, String dest, String body) throws Exception {
        if (!SmsRole.isDefault(c)) throw new IllegalStateException("Set SimpleSMS as your default SMS app first");
        if (c.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Grant permission to send SMS first");
        String address = dest == null ? "" : PhoneNumberUtils.stripSeparators(dest.trim());
        if (!address.matches("\\+?[0-9*#]+")) throw new IllegalArgumentException("Enter one phone number");
        if (body == null || body.trim().isEmpty()) throw new IllegalArgumentException("Enter a message");
        int sub = SubscriptionManager.getDefaultSmsSubscriptionId();
        if (!SubscriptionManager.isValidSubscriptionId(sub))
            throw new IllegalStateException("Choose a default SMS SIM in Android settings first");
        SmsManager manager;
        if (Build.VERSION.SDK_INT >= 31) {
            SmsManager service = c.getSystemService(SmsManager.class);
            if (service == null) throw new IllegalStateException("SMS service is unavailable");
            manager = service.createForSubscriptionId(sub);
        } else manager = SmsManager.getSmsManagerForSubscriptionId(sub);
        ArrayList<String> parts = manager.divideMessage(body);
        if (parts == null || parts.isEmpty()) throw new IllegalArgumentException("Could not divide SMS");
        if (parts.size() > 255) throw new IllegalArgumentException("Message exceeds 255 SMS parts");
        boolean reports = AppPrefs.deliveryReports(c);
        EncryptedSmsStore store = EncryptedSmsStore.get(c);
        SmsRepository.Message m = new SmsRepository.Message();
        m.address = address; m.body = body; m.date = System.currentTimeMillis(); m.read = true;
        m.threadId = store.threadFor(address); m.type = Telephony.Sms.MESSAGE_TYPE_OUTBOX;
        m.subscriptionId = sub; m.sentParts = new int[parts.size()];
        if (reports) { m.status = Telephony.Sms.STATUS_PENDING; m.deliveryParts = new int[parts.size()]; }
        long id = store.insert(m);
        ArrayList<PendingIntent> sent = new ArrayList<>();
        ArrayList<PendingIntent> delivery = reports ? new ArrayList<>() : null;
        for (int part = 0; part < parts.size(); part++) {
            Intent si = new Intent(c, SendStatusReceiver.class).setAction(sentAction(c))
                    .setData(Uri.parse("simplesms://sent/" + id + "/" + part)).putExtra(ID, id).putExtra(PART, part);
            sent.add(PendingIntent.getBroadcast(c, 0, si, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            if (reports) {
                Intent di = new Intent(c, DeliveryStatusReceiver.class).setAction(deliveryAction(c))
                        .setData(Uri.parse("simplesms://delivery/" + id + "/" + part)).putExtra(ID, id).putExtra(PART, part);
                int flag = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
                delivery.add(PendingIntent.getBroadcast(c, 0, di, PendingIntent.FLAG_UPDATE_CURRENT | flag));
            }
        }
        try {
            if (parts.size() == 1) manager.sendTextMessage(address, null, parts.get(0), sent.get(0), reports ? delivery.get(0) : null);
            else manager.sendMultipartTextMessage(address, null, parts, sent, delivery);
        } catch (Exception e) {
            store.failSend(id, -1); SmsRepository.changed(); throw e;
        }
        AppPrefs.archive(c, m.threadId, false); SmsRepository.changed(); return m.threadId;
    }
}
