package com.android.messaging.sms;
import android.app.PendingIntent;
import android.content.*;
import android.net.Uri;
import android.telephony.*;
import com.android.messaging.security.Vault;
import com.android.messaging.util.AppPrefs;
import org.json.JSONObject;
import java.util.*;
public final class SmsSender {
 public static final String SENT="com.zoeykl.simplesms.SENT",DELIVERED="com.zoeykl.simplesms.DELIVERED";
 private SmsSender(){}
 public static long send(Context c,String destination,String body)throws Exception{
  Vault.require();if(!SmsRole.isDefault(c))throw new SecurityException("Set SimpleSMS as the default SMS app first");String a=destination==null?"":destination.trim();if(a.isEmpty()||body==null||body.trim().isEmpty())throw new IllegalArgumentException("Enter a recipient and message");
  long thread=Vault.thread(a);int sub=SubscriptionManager.getDefaultSmsSubscriptionId();if(sub<0||sub==Integer.MAX_VALUE)throw new IllegalStateException("Select a default SMS SIM in Android settings");SmsManager sm=SmsManager.getSmsManagerForSubscriptionId(sub);ArrayList<String> parts=sm.divideMessage(body);boolean reports=AppPrefs.deliveryReports(c);
  long id=TransportStore.store(c,new JSONObject().put("address",a).put("body",body).put("date",System.currentTimeMillis()).put("type",4).put("read",1).put("subscription",sub).put("total_parts",parts.size()));
  ArrayList<PendingIntent> sent=new ArrayList<>(),delivered=reports?new ArrayList<>():null;
  for(int n=0;n<parts.size();n++){
   Intent si=new Intent(c,SendStatusReceiver.class).setAction(SENT).setData(Uri.parse("private-sms:"+id+"/sent/"+n)).putExtra("message",id).putExtra("part",n).putExtra("count",parts.size());sent.add(PendingIntent.getBroadcast(c,0,si,PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_IMMUTABLE));
   if(reports){Intent di=new Intent(c,DeliveryStatusReceiver.class).setAction(DELIVERED).setData(Uri.parse("private-sms:"+id+"/delivered/"+n)).putExtra("message",id).putExtra("part",n).putExtra("count",parts.size());delivered.add(PendingIntent.getBroadcast(c,0,di,PendingIntent.FLAG_ONE_SHOT|(android.os.Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0)));}
  }
  try{if(parts.size()==1)sm.sendTextMessage(a,null,parts.get(0),sent.get(0),reports?delivered.get(0):null);else sm.sendMultipartTextMessage(a,null,parts,sent,delivered);}catch(Exception e){TransportStore.event(c,id,new JSONObject().put("type",5));throw e;}return thread;
 }
}
