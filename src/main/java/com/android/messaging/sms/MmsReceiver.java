package com.android.messaging.sms;
import android.content.*;
import android.provider.Telephony;
import com.google.android.mms.pdu_alt.*;
import com.android.messaging.util.NotificationHelper;
import org.json.JSONObject;
public final class MmsReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent i){if(!Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION.equals(i.getAction())||!SmsRole.isDefault(context))return;byte[] data=i.getByteArrayExtra("data");if(data==null||data.length>MmsTransport.MAX_BYTES)return;PendingResult result=goAsync();Context c=context.getApplicationContext();new Thread(()->{try{GenericPdu p=new PduParser(data).parse();if(!(p instanceof NotificationInd))return;NotificationInd n=(NotificationInd)p;int sub=i.getIntExtra("subscription",-1);String from=n.getFrom()==null?"Unknown":n.getFrom().getString(),location=MmsStore.text(n.getContentLocation());if(location.isEmpty())return;TransportStore.store(c,new JSONObject().put("address",from).put("body","MMS ready to download").put("date",System.currentTimeMillis()).put("type",1).put("read",0).put("mms",true).put("pending",true).put("location",location).put("transaction",MmsStore.text(n.getTransactionId())).put("subscription",sub));NotificationHelper.incoming(c,null,0,null);try{MmsTransport.control(c,new NotifyRespInd(PduHeaders.CURRENT_MMS_VERSION,n.getTransactionId(),PduHeaders.STATUS_DEFERRED),location,sub);}catch(Exception ignored){}}catch(Exception failure){NotificationHelper.failure(c);}finally{result.finish();}},"private-mms-notification").start();}
}
