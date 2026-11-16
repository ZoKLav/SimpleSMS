package com.android.messaging.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.android.messaging.security.Vault;
import com.android.messaging.security.VaultKeys;

/** Direct notification actions have no activity launch or notification trampoline. */
public final class NotificationCommandReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null)return;long id=intent.getLongExtra("message_id",0);String operation=intent.getStringExtra("operation");
        if(id==0||!("read".equals(operation)||"delete".equals(operation)))return;
        Context app=context.getApplicationContext();PendingResult result=goAsync();
        Workers.INBOUND.execute(()->{
            try{
                if(!Vault.unlocked()&&!VaultKeys.lockEnabled(app))Vault.unlock(app);
                if(!NotificationCommand.mayRun(app)){NotificationHelper.redact(app);return;}
                // A stale direct-delete button must not bypass a subsequently enabled confirmation setting.
                if("delete".equals(operation)&&AppPrefs.confirmDelete(app))return;
                NotificationCommand.execute(app,id,operation);
            }catch(Exception unavailable){NotificationHelper.failure(app);}finally{result.finish();}
        });
    }
}
