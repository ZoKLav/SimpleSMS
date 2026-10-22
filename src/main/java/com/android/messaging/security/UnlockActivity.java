package com.android.messaging.security;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.*;
import com.android.messaging.ui.conversationlist.ConversationListActivity;
public final class UnlockActivity extends Activity {
 private static final int AUTH=810;private TextView status;private Button unlock,disable;private boolean working;private String change;
 @Override protected void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);change=getIntent().getStringExtra("lock_mode");String pending=VaultKeys.pendingChange(this);if(pending!=null)change=pending;
  LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(32,48,32,32);TextView title=new TextView(this);title.setText("SimpleSMS");title.setTextSize(26);box.addView(title);status=new TextView(this);status.setText("App lock is optional. Private messages, attachments, drafts and preferences stay encrypted in either mode. With app lock off, the app can use its key without another credential prompt; rely on your device's access controls. Shared Android messages remain in their existing storage. Carrier SMS/MMS is not end-to-end encrypted.");status.setPadding(0,24,0,24);box.addView(status);
  unlock=new Button(this);unlock.setText("on".equals(change)?"Enable app lock":"Unlock");unlock.setOnClickListener(v->authenticate());box.addView(unlock);
  disable=new Button(this);disable.setText("Turn off app lock");disable.setOnClickListener(v->{change="off";authenticate();});box.addView(disable);
  Button security=new Button(this);security.setText("Device security settings");security.setOnClickListener(v->startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)));box.addView(security);setContentView(box);
  if(!VaultKeys.lockEnabled(this)&&!"on".equals(change)){disable.setVisibility(android.view.View.GONE);open(false);}else if("on".equals(change)){disable.setVisibility(android.view.View.GONE);}
 }
 private void authenticate(){if(working)return;working=true;unlock.setEnabled(false);disable.setEnabled(false);new Thread(()->{try{VaultKeys.ensure(this);if("on".equals(change))VaultKeys.prepare(this,true);runOnUiThread(()->{KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);Intent auth=km==null?null:km.createConfirmDeviceCredentialIntent("SimpleSMS app lock","Confirm your device credential");if(auth==null){failure("Set a secure device screen lock to use app lock");return;}startActivityForResult(auth,AUTH);});}catch(Exception e){runOnUiThread(()->failure(e.getMessage()));}},"vault-key-setup").start();}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=AUTH)return;if(result!=RESULT_OK){failure("Authentication canceled");return;}open(true);}
 private void open(boolean authenticated){working=true;unlock.setEnabled(false);new Thread(()->{try{Vault.unlock(this);if(authenticated&&change!=null)Vault.setAppLock(this,"on".equals(change));try{com.android.messaging.sms.TransportStore.reconcile(this);}catch(Exception pending){/* Originals remain until a verified retry succeeds. */}runOnUiThread(()->{Intent next=new Intent(this,ConversationListActivity.class);
if(getIntent().hasExtra("notification_message"))next=new Intent(this,com.android.messaging.util.NotificationActionActivity.class).putExtra("message_id",getIntent().getLongExtra("notification_message",0)).putExtra("operation",getIntent().getStringExtra("notification_operation"));
startActivity(next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();});}catch(Exception e){Vault.lock();runOnUiThread(()->failure("Cannot open encrypted storage: "+e.getMessage()));}},"vault-unlock").start();}
 private void failure(String reason){status.setText(reason==null?"Encrypted storage unavailable":reason);working=false;unlock.setEnabled(true);disable.setEnabled(true);}
}
