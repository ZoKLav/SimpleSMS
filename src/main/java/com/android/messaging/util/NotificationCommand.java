package com.android.messaging.util;

import android.content.Context;
import android.app.KeyguardManager;
import com.android.messaging.security.Vault;
import com.android.messaging.security.VaultKeys;
import com.android.messaging.sms.SmsRepository;
import java.util.Collections;

/** Shared execution gate for notification UI and background actions. Never unlocks protected sessions. */
public final class NotificationCommand {
    private NotificationCommand(){}
    static boolean mayRun(Context context){
        KeyguardManager manager=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
        return Vault.unlocked()&&!(VaultKeys.lockEnabled(context)&&manager!=null&&manager.isDeviceLocked());
    }
    public static void execute(Context context,long id,String operation)throws Exception{
        if(!mayRun(context))throw new SecurityException("Unlock SimpleSMS to complete this action");
        if(!AppPrefs.notificationActions(context))return;
        if("read".equals(operation)){
            SmsRepository.Message message=SmsRepository.message(context,id);
            if(message!=null)SmsRepository.markRead(context,Collections.singletonList(message));
        }else if("delete".equals(operation))SmsRepository.deleteMessage(context,id);
        else throw new IllegalArgumentException("Unsupported notification command");
        NotificationHelper.dismiss(context,id);
    }
}
