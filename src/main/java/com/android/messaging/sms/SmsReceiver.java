package com.android.messaging.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import com.android.messaging.storage.EncryptedSmsStore;
import com.android.messaging.util.AppPrefs;
import com.android.messaging.util.NotificationHelper;
import com.android.messaging.util.Workers;

public final class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())) return;
        PendingResult pending = goAsync();
        Workers.INBOUND.execute(() -> {
            try {
                SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
                if (parts == null || parts.length == 0 || parts[0] == null) return;
                SmsMessage first = parts[0];
                String address = first.getDisplayOriginatingAddress();
                if (address == null) address = first.getOriginatingAddress();
                if (address == null || address.isEmpty()) address = "Unknown";
                StringBuilder body = new StringBuilder();
                for (SmsMessage part : parts) if (part != null && part.getMessageBody() != null) body.append(part.getMessageBody());
                EncryptedSmsStore store = EncryptedSmsStore.get(c);
                SmsRepository.Message m = new SmsRepository.Message();
                m.address = address; m.body = body.toString(); m.date = System.currentTimeMillis();
                m.dateSent = Math.max(0, first.getTimestampMillis()); m.type = Telephony.Sms.MESSAGE_TYPE_INBOX;
                m.threadId = store.threadFor(address);
                m.subscriptionId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                        intent.getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID));
                store.insert(m); AppPrefs.archive(c, m.threadId, false); SmsRepository.changed();
                // Saving and notifying are independent: a denied notification must not erase the SMS.
                try { NotificationHelper.incoming(c, address, m.threadId, m.body); } catch (Exception ignored) { }
            } catch (Exception e) {
                try { NotificationHelper.storageError(c); } catch (Exception ignored) { }
            } finally { pending.finish(); }
        });
    }
}
