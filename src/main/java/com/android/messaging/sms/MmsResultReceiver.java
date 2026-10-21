package com.android.messaging.sms;
import android.app.Activity;
import android.content.*;
import android.net.Uri;
import com.android.messaging.security.*;
import com.android.messaging.util.NotificationHelper;
import com.google.android.mms.pdu_alt.*;
import org.json.JSONObject;
public final class MmsResultReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent i){Uri transfer=i.getData();if(transfer==null)return;String action=i.getAction();int code=getResultCode();long id=i.getLongExtra("message",-1);byte[] response=i.getByteArrayExtra("android.telephony.extra.MMS_DATA");PendingResult result=goAsync();Context c=context.getApplicationContext();new Thread(()->{try{
  if(MmsTransport.SEND.equals(action)&&id!=-1){boolean ok=code==Activity.RESULT_OK;if(ok&&response!=null){GenericPdu p=new PduParser(response).parse();ok=p instanceof SendConf&&((SendConf)p).getResponseStatus()==PduHeaders.RESPONSE_STATUS_OK;}TransportStore.event(c,id,new JSONObject().put("type",ok?2:5).put("error",ok?0:code));if(!ok)NotificationHelper.failure(c);}
  else if(MmsTransport.DOWNLOAD.equals(action)){TransferProvider.Lease lease=TransferProvider.find(transfer);if(code==Activity.RESULT_OK&&lease!=null)lease.finished.await(7,java.util.concurrent.TimeUnit.SECONDS);if(code!=Activity.RESULT_OK||lease==null||!lease.saved)NotificationHelper.failure(c);}
 }catch(Exception failure){NotificationHelper.failure(c);}finally{TransferProvider.release(transfer);c.revokeUriPermission(transfer,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);result.finish();}},"private-mms-result").start();}
}
