package com.android.messaging.sms;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.android.messaging.storage.EncryptedSmsStore;
import com.android.messaging.util.NotificationHelper;
import com.android.messaging.util.Workers;

public final class SendStatusReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (intent == null || !SmsSender.sentAction(c).equals(intent.getAction())) return;
        final int result = getResultCode(), part = intent.getIntExtra(SmsSender.PART, -1);
        final long id = intent.getLongExtra(SmsSender.ID, 0), when = System.currentTimeMillis();
        if (id >= 0 || part < 0) return;
        PendingResult pending = goAsync();
        Workers.INBOUND.execute(() -> {
            try { EncryptedSmsStore.get(c).sentPart(id, part, result == Activity.RESULT_OK, result, when); SmsRepository.changed(); }
            catch (Exception e) { try { NotificationHelper.storageError(c); } catch (Exception ignored) { } }
            finally { pending.finish(); }
        });
    }
}
