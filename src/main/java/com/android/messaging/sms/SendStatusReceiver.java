package com.android.messaging.sms;
import android.app.Activity;
import android.content.*;
import com.android.messaging.security.Vault;
import org.json.JSONObject;
/** Append encrypted status events, including when the user has locked the app. */
public final class SendStatusReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent i){if(!SmsSender.SENT.equals(i.getAction()))return;long id=i.getLongExtra("message",-1);if(id==-1)return;int code=getResultCode();PendingResult result=goAsync();Context c=context.getApplicationContext();new Thread(()->{try{TransportStore.event(c,id,new JSONObject().put("sent_part",i.getIntExtra("part",0)).put("part_count",i.getIntExtra("count",1)).put("part_result",code));}catch(Exception e){com.android.messaging.util.NotificationHelper.failure(c);}finally{result.finish();}},"private-sms-status").start();}
}
