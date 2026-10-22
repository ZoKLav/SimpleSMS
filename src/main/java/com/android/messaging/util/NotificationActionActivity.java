package com.android.messaging.util;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;
import com.android.messaging.security.*;
import com.android.messaging.sms.SmsRepository;
import com.android.messaging.ui.conversation.ConversationActivity;

/** Internal, fixed-target entry point; deletion never bypasses message/app locks. */
public final class NotificationActionActivity extends Activity {
    private boolean working;
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    @Override public void onResume(){
        super.onResume();if(working)return;working=true;
        KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        long id=getIntent().getLongExtra("message_id",0);
        String op=getIntent().getStringExtra("operation");
        if(!Vault.unlocked()||(VaultKeys.lockEnabled(this)&&km!=null&&km.isDeviceLocked())){
            if(VaultKeys.lockEnabled(this))Vault.lock();
            startActivity(new Intent(this,UnlockActivity.class).putExtra("notification_message",id).putExtra("notification_operation",op));finish();return;
        }
        new Thread(()->{try{
            SmsRepository.Message m=SmsRepository.message(this,id);
            runOnUiThread(()->{
                if(isFinishing()||!Vault.unlocked()){finish();return;}
                if(m==null){Toast.makeText(this,"This message is no longer available",Toast.LENGTH_LONG).show();NotificationHelper.dismiss(this,id);finish();return;}
                if(("delete".equals(op)||"reply".equals(op))&&!AppPrefs.notificationActions(this)){finish();return;}
                if("delete".equals(op)){
                    new AlertDialog.Builder(this).setTitle("Delete message?").setMessage("Permanently delete this message and its attachments. Locked messages are kept.")
                        .setPositiveButton("Delete",(d,w)->new Thread(()->{try{Vault.require();SmsRepository.deleteMessage(this,id);NotificationHelper.dismiss(this,id);runOnUiThread(this::finish);}catch(Exception e){runOnUiThread(()->fail(e));}},"notification-delete").start())
                        .setNegativeButton("Cancel",(d,w)->finish()).setOnCancelListener(d->finish()).show();
                }else{
                    startActivity(new Intent(this,ConversationActivity.class).putExtra(ConversationActivity.EXTRA_THREAD_ID,m.threadId).putExtra("notification_reply_id",id));finish();
                }
            });
        }catch(Exception e){runOnUiThread(()->fail(e));}},"notification-open").start();
    }
    private void fail(Exception e){Toast.makeText(this,"Cannot perform action: "+e.getMessage(),Toast.LENGTH_LONG).show();finish();}
}
