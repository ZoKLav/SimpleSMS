package com.android.messaging.util;

import android.app.RemoteInput;
import android.content.*;
import android.os.Bundle;
import com.android.messaging.security.*;
import com.android.messaging.sms.*;

/** Direct replies use the same sending/routing code as the composer. */
public final class NotificationReplyReceiver extends BroadcastReceiver {
    private static final java.util.Set<Long> inFlight=java.util.Collections.synchronizedSet(new java.util.HashSet<Long>());
    @Override public void onReceive(Context context,Intent intent){
        Bundle input=RemoteInput.getResultsFromIntent(intent);
        CharSequence value=input==null?null:input.getCharSequence("reply");
        if(value==null||value.toString().trim().isEmpty())return;
        final String body=value.toString();final long id=intent.getLongExtra("message_id",0);
        if(!inFlight.add(id))return;
        PendingResult pending=goAsync();Context c=context.getApplicationContext();
        new Thread(()->{boolean saved=false;try{
            long draftId=NotificationDrafts.save(c,id,body);saved=true;
            // An old inline action must not defeat app lock enabled since it was posted.
            if(VaultKeys.lockEnabled(c))throw new SecurityException("Unlock SimpleSMS to reply");
            if(!Vault.unlocked())Vault.unlock(c);
            if(!AppPrefs.notificationActions(c))return;
            SmsRepository.Message m=SmsRepository.message(c,id);
            if(m==null)throw new IllegalStateException("Message unavailable");
            // Preserve any existing composer draft. The reply itself stays encrypted until sent.

            if(m.address.contains(",")||m.address.contains(";"))MmsTransport.send(c,m.address,body,null);
            else SmsSender.send(c,m.address,body);
            try{Vault.erase(c,draftId);}catch(Exception cleanup){NotificationHelper.failure(c);}
            NotificationHelper.dismiss(c,id);
        }catch(Exception e){NotificationHelper.replyFailed(c,id,saved);}finally{inFlight.remove(id);pending.finish();}},"notification-reply").start();
    }
}
