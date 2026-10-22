package com.android.messaging.sms;
import android.content.Context;
import android.util.Base64;
import com.google.android.mms.pdu_alt.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Decode carrier PDUs in RAM and seal directly to the private store. */
public final class MmsStore {
 private MmsStore(){}
 public static String text(byte[] b){return b==null?"":new String(b,StandardCharsets.UTF_8);}
 public static JSONObject record(MultimediaMessagePdu p,int type,int sub)throws Exception{
  JSONObject record=new JSONObject().put("mms",true).put("type",type).put("subscription",sub).put("date",p.getDate()>0?p.getDate()*1000:System.currentTimeMillis()).put("read",type==1?0:1);List<String> to=new ArrayList<>();JSONArray addresses=new JSONArray();
  if(p.getFrom()!=null)addresses.put(new JSONObject().put("type",137).put("address",p.getFrom().getString()));if(p.getTo()!=null)for(EncodedStringValue a:p.getTo()){to.add(a.getString());addresses.put(new JSONObject().put("type",151).put("address",a.getString()));}
  EncodedStringValue[] cc=p instanceof RetrieveConf?((RetrieveConf)p).getCc():p instanceof SendReq?((SendReq)p).getCc():null;if(cc!=null)for(EncodedStringValue a:cc)addresses.put(new JSONObject().put("type",130).put("address",a.getString()));
  if(p instanceof SendReq)record.put("transaction",text(((SendReq)p).getTransactionId()));
  record.put("address",type==1?(p.getFrom()==null?"Unknown":p.getFrom().getString()):android.text.TextUtils.join(",",to));record.put("addresses",addresses);
  StringBuilder body=new StringBuilder();if(p.getSubject()!=null)body.append("Subject: ").append(p.getSubject().getString());JSONArray parts=new JSONArray();PduBody content=p.getBody();if(content==null)throw new java.io.IOException("Missing MMS body");
  for(int i=0;i<content.getPartsNum();i++){PduPart part=content.getPart(i);String mime=text(part.getContentType());byte[] data=part.getData();if(data==null)data=new byte[0];if("text/plain".equals(mime)){String charset="UTF-8";try{if(part.getCharset()!=0)charset=CharacterSets.getMimeName(part.getCharset());}catch(Exception ignored){}if(body.length()>0)body.append('\n');body.append(new String(data,charset));}else if(!"application/smil".equals(mime))parts.put(new JSONObject().put("mime",mime).put("name",text(part.getName())).put("data",Base64.encodeToString(data,Base64.NO_WRAP)));}
  record.put("body",body.toString());record.put("parts",parts);return record;
 }
 public static long receive(Context c,byte[] data,int sub,long parent,String location)throws Exception{GenericPdu p=new PduParser(data).parse();if(!(p instanceof RetrieveConf))throw new java.io.IOException("Invalid MMS download");JSONObject result=record((RetrieveConf)p,1,sub).put("parent",parent).put("location",location).put("transaction",text(((RetrieveConf)p).getTransactionId()));if(parent<0)try(android.database.Cursor x=c.getContentResolver().query(PublicStore.uri(parent),new String[]{"read","locked"},null,null,null)){if(x!=null&&x.moveToFirst())result.put("read",x.getInt(0)).put("locked",x.getInt(1)!=0);}long id=TransportStore.store(c,result,parent>=0);com.android.messaging.util.NotificationHelper.dismiss(c,parent);com.android.messaging.util.NotificationHelper.incoming(c,id,result.optString("address"),result.optString("body"),true);return id;}
}
