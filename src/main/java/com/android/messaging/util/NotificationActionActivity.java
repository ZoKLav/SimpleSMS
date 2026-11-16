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
    @Override public void onCreate(Bundle state){setTheme(AppPrefs.isDark(this)?com.android.messaging.R.style.NotificationActionTheme_Dark:com.android.messaging.R.style.NotificationActionTheme);super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    @Override public void onResume(){
        super.onResume();if(working)return;working=true;
        KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        long id=getIntent().getLongExtra("message_id",0);
        String op=getIntent().getStringExtra("operation");
        if(!Vault.unlocked()||(VaultKeys.lockEnabled(this)&&km!=null&&km.isDeviceLocked())){
            if(VaultKeys.lockEnabled(this))Vault.lock();
            startActivity(new Intent(this,UnlockActivity.class).putExtra("notification_message",id).putExtra("notification_operation",op));finish();return;
        }
        final android.content.Context app=getApplicationContext();UiWork.run(this,()->SmsRepository.message(app,id),(activity,message,error)->{if(!Vault.unlocked()){activity.finish();return;}if(error!=null){activity.fail(error);return;}activity.loaded(id,op,message);});
    }
    private void loaded(long id,String operation,SmsRepository.Message message){
        if(message==null){Toast.makeText(this,"This message is no longer available",Toast.LENGTH_LONG).show();NotificationHelper.dismiss(this,id);finish();return;}
        if(("delete".equals(operation)||"read".equals(operation)||"reply".equals(operation))&&!AppPrefs.notificationActions(this)){finish();return;}
        if("read".equals(operation)){perform(id,"read");return;}
        if("delete".equals(operation)&&!AppPrefs.confirmDelete(this)){perform(id,"delete");return;}
        if("delete".equals(operation))new ScreenDialog.Builder(this).setTitle("Delete message?").setMessage("Permanently delete this message and its attachments. Locked messages are kept.").setPositiveButton("Delete",(dialog,which)->delete(id)).setNegativeButton("Cancel",(dialog,which)->finish()).setOnCancelListener(dialog->finish()).show();
        else{startActivity(new Intent(this,ConversationActivity.class).putExtra(ConversationActivity.EXTRA_THREAD_ID,message.threadId).putExtra(ConversationActivity.EXTRA_ADDRESS,message.address).putExtra("notification_reply_id",id));finish();}
    }
    private void delete(long id){perform(id,"delete");}
    private void perform(long id,String operation){final android.content.Context app=getApplicationContext();UiWork.run(this,()->{NotificationCommand.execute(app,id,operation);return true;},(activity,value,error)->{if(error!=null)activity.fail(error);else activity.finish();});}
    private void fail(Exception e){Toast.makeText(this,"Cannot perform action: "+e.getMessage(),Toast.LENGTH_LONG).show();finish();}
}
