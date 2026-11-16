package com.android.messaging.security;
import com.android.messaging.R;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.*;
import com.android.messaging.ui.conversationlist.ConversationListActivity;
public final class UnlockActivity extends Activity {
 private static final int AUTH=810;private TextView status;private Button unlock,disable;private boolean working;private String change;
 @Override protected void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);change=getIntent().getStringExtra("lock_mode");String pending=VaultKeys.pendingChange(this);if(pending!=null)change=pending;
  LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(32,48,32,32);TextView title=new TextView(this);title.setText(R.string.app_name);title.setTextSize(26);box.addView(title);status=new TextView(this);status.setText(R.string.text_app_lock_is_optional_private_messages_attachments);status.setPadding(0,24,0,24);box.addView(status);
  unlock=new Button(this);unlock.setText("on".equals(change)?"Enable app lock":"Unlock");unlock.setOnClickListener(v->authenticate());box.addView(unlock);
  disable=new Button(this);disable.setText(R.string.text_turn_off_app_lock);disable.setOnClickListener(v->{change="off";authenticate();});box.addView(disable);
  Button security=new Button(this);security.setText(R.string.text_device_security_settings);security.setOnClickListener(v->startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)));box.addView(security);setContentView(box);
  if(!VaultKeys.lockEnabled(this)&&!"on".equals(change)){disable.setVisibility(android.view.View.GONE);open(false);}else if("on".equals(change)){disable.setVisibility(android.view.View.GONE);}
 }
 private void authenticate(){
  if(working)return;working=true;unlock.setEnabled(false);disable.setEnabled(false);Context app=getApplicationContext();boolean enable="on".equals(change);
  com.android.messaging.util.UiWork.run(this,()->{VaultKeys.ensure(app);if(enable)VaultKeys.prepare(app,true);return true;},(activity,value,error)->{if(error!=null){activity.failure(error.getMessage());return;}KeyguardManager manager=(KeyguardManager)activity.getSystemService(KEYGUARD_SERVICE);Intent auth=manager==null?null:manager.createConfirmDeviceCredentialIntent("SimpleSMS app lock","Confirm your device credential");if(auth==null)activity.failure("Set a secure device screen lock to use app lock");else activity.startActivityForResult(auth,AUTH);});
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=AUTH)return;if(result!=RESULT_OK){failure("Authentication canceled");return;}open(true);}
 private void open(boolean authenticated){working=true;unlock.setEnabled(false);Context app=getApplicationContext();String mode=change;
  com.android.messaging.util.UiWork.run(this,()->{try{Vault.unlock(app);if(authenticated&&mode!=null)Vault.setAppLock(app,"on".equals(mode));com.android.messaging.util.Workers.MAINTENANCE.execute(()->{try{com.android.messaging.sms.TransportStore.reconcile(app);}catch(Exception pending){}});return true;}catch(Exception error){Vault.lock();throw error;}},(activity,value,error)->{if(error!=null){activity.failure("Cannot open encrypted storage: "+error.getMessage());return;}Intent next=new Intent(activity,ConversationListActivity.class);if(activity.getIntent().hasExtra("notification_message"))next=new Intent(activity,com.android.messaging.util.NotificationActionActivity.class).putExtra("message_id",activity.getIntent().getLongExtra("notification_message",0)).putExtra("operation",activity.getIntent().getStringExtra("notification_operation"));activity.startActivity(next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));activity.finish();});
 }
 private void failure(String reason){status.setText(reason==null?"Encrypted storage unavailable":reason);working=false;unlock.setEnabled(true);disable.setEnabled(true);}
}
