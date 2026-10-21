package com.android.messaging.sms;
import android.content.*;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import com.android.messaging.util.NotificationHelper;
import org.json.JSONObject;
public final class SmsReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent i){if(i==null||!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(i.getAction())||!SmsRole.isDefault(context))return;PendingResult result=goAsync();Context c=context.getApplicationContext();new Thread(()->{try{SmsMessage[] parts=Telephony.Sms.Intents.getMessagesFromIntent(i);if(parts==null||parts.length==0||parts[0]==null)return;String address=parts[0].getDisplayOriginatingAddress();if(address==null)address="Unknown";StringBuilder body=new StringBuilder();for(SmsMessage part:parts)if(part!=null&&part.getMessageBody()!=null)body.append(part.getMessageBody());TransportStore.store(c,new JSONObject().put("address",address).put("body",body.toString()).put("date",System.currentTimeMillis()).put("date_sent",parts[0].getTimestampMillis()).put("type",1).put("read",0).put("subscription",i.getIntExtra("subscription",-1)));NotificationHelper.incoming(c,null,0,null);}catch(Exception failure){NotificationHelper.failure(c);}finally{result.finish();}},"private-sms-receive").start();}
}
