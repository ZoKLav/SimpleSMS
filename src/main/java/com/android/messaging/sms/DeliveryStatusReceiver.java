package com.android.messaging.sms;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsMessage;
import com.android.messaging.storage.EncryptedSmsStore;
import com.android.messaging.util.NotificationHelper;
import com.android.messaging.util.Workers;

public final class DeliveryStatusReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (intent == null || !SmsSender.deliveryAction(c).equals(intent.getAction())) return;
        final int result = getResultCode(), part = intent.getIntExtra(SmsSender.PART, -1);
        final long id = intent.getLongExtra(SmsSender.ID, 0), when = System.currentTimeMillis();
        if (id >= 0 || part < 0) return;
        PendingResult pending = goAsync();
        Workers.INBOUND.execute(() -> {
            try {
                boolean success = result == Activity.RESULT_OK;
                byte[] pdu = intent.getByteArrayExtra("pdu");
                if (success && pdu != null) {
                    String format = intent.getStringExtra("format");
                    SmsMessage report = SmsMessage.createFromPdu(pdu, format == null ? "3gpp" : format);
                    if (report != null) {
                        int status = report.getStatus();
                        if (!"3gpp2".equals(format) && status >= 0x20 && status < 0x40) return; // Carrier still retrying.
                        success = "3gpp2".equals(format) ? status == 0 : status >= 0 && status < 0x20;
                    }
                }
                EncryptedSmsStore.get(c).deliveryPart(id, part, success, when); SmsRepository.changed();
            } catch (Exception e) { try { NotificationHelper.storageError(c); } catch (Exception ignored) { } }
            finally { pending.finish(); }
        });
    }
}
