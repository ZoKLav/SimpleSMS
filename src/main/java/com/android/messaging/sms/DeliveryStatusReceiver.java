package com.android.messaging.sms;
import android.app.Activity;
import android.content.*;
import android.telephony.SmsMessage;
import com.android.messaging.security.Vault;
import org.json.JSONObject;
public final class DeliveryStatusReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent i){if(!SmsSender.DELIVERED.equals(i.getAction()))return;long id=i.getLongExtra("message",-1);if(id==-1)return;int code=getResultCode();byte[] bytes=i.getByteArrayExtra("pdu");String format=i.getStringExtra("format");PendingResult result=goAsync();Context c=context.getApplicationContext();new Thread(()->{try{int status=-1;if(bytes!=null){SmsMessage report=SmsMessage.createFromPdu(bytes,format);if(report!=null)status=report.getStatus();}TransportStore.event(c,id,new JSONObject().put("delivery_part",i.getIntExtra("part",0)).put("part_count",i.getIntExtra("count",1)).put("delivery_status",status).put("delivery_result",code));}catch(Exception ignored){}finally{result.finish();}},"private-sms-delivery").start();}
}
