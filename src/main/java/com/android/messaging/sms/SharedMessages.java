package com.android.messaging.sms;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import com.android.messaging.security.Vault;
import com.google.android.mms.pdu_alt.EncodedStringValue;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Live provider reads. No import, encryption conversion, or deletion occurs while listing. */
public final class SharedMessages {
 private SharedMessages(){}
 public static List<SmsRepository.Message> read(Context c,long thread,Map<String,org.json.JSONObject> privateSources)throws Exception{
  List<SmsRepository.Message> out=new ArrayList<>();if(c.checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED)return out;
  try(Cursor x=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id","address","body","date","type","read","status","locked","thread_id","sub_id"},null,null,"date ASC")){
   if(x!=null)while(x.moveToNext()){String source="sms:"+x.getLong(0);SmsRepository.Message m=sms(x);if(covered(m,privateSources.get(source)))continue;m.threadId=Vault.thread(m.address);if(thread>=0&&m.threadId!=thread)continue;out.add(m);}
  }
  try(Cursor x=c.getContentResolver().query(Telephony.Mms.CONTENT_URI,new String[]{"_id","date","msg_box","read","locked","m_type","ct_l","tr_id","sub_id","sub","sub_cs","thread_id"},"m_type IN (128,130,132)",null,"date ASC")){
   if(x!=null)while(x.moveToNext()){
    long id=x.getLong(0);String source="mms:"+id;SmsRepository.Message m=mmsHeader(x);m.address=addresses(c,id,m.type==1);m.threadId=Vault.thread(m.address);if(thread>=0&&m.threadId!=thread)continue;
    StringBuilder body=new StringBuilder();if(!x.isNull(9)){body.append("Subject: ").append(new EncodedStringValue(x.getInt(10),x.getString(9).getBytes(StandardCharsets.ISO_8859_1)).getString());}
    try(Cursor part=c.getContentResolver().query(Uri.parse("content://mms/"+id+"/part"),new String[]{"_id","ct","name","text","chset"},null,null,"seq ASC, _id ASC")){
     if(part!=null)while(part.moveToNext()){String mime=part.getString(1);Uri uri=Uri.parse("content://mms/part/"+part.getLong(0));if("text/plain".equals(mime)||"text/html".equals(mime)){
       String text=part.getString(3);if(text==null)try(java.io.InputStream in=c.getContentResolver().openInputStream(uri)){byte[] bytes=MmsTransport.readBounded(in,MmsTransport.MAX_BYTES);text=new EncodedStringValue(part.getInt(4),bytes).getString();}catch(java.io.IOException unavailable){text="[Text unavailable]";}
       if(body.length()>0)body.append('\n');body.append(text);
      }else if(!"application/smil".equals(mime)){SmsRepository.Part item=new SmsRepository.Part();item.mime=mime==null?"application/octet-stream":mime;item.name=part.isNull(2)?"attachment":part.getString(2);item.data=uri.toString();item.shared=true;m.parts.add(item);}
     }
    }m.body=body.toString();if(!covered(m,privateSources.get(source)))out.add(m);
   }
  }return out;
 }
 static boolean covered(SmsRepository.Message shared,org.json.JSONObject copy){return copy!=null&&copy.optLong("date")==shared.date&&copy.optBoolean("mms")==shared.mms&&copy.optBoolean("pending")==shared.pending&&shared.address.equals(copy.optString("address","Unknown"))&&shared.body.equals(copy.optString("body",""));}
 static SmsRepository.Message sms(Cursor x){SmsRepository.Message m=new SmsRepository.Message();m.id=providerId(x.getLong(0),false);m.source="sms:"+x.getLong(0);m.shared=true;m.providerThread=x.getLong(8);m.address=x.isNull(1)?"Unknown":x.getString(1);m.body=x.isNull(2)?"":x.getString(2);m.date=x.getLong(3);m.type=x.getInt(4);m.read=x.getInt(5);m.status=x.getInt(6);m.locked=x.getInt(7)!=0;m.subscription=x.getInt(9);return m;}
 static SmsRepository.Message mmsHeader(Cursor x){SmsRepository.Message m=new SmsRepository.Message();m.id=providerId(x.getLong(0),true);m.source="mms:"+x.getLong(0);m.shared=true;m.mms=true;m.providerThread=x.getLong(11);m.type=x.getInt(2);m.date=x.getLong(1)*1000;m.read=x.getInt(3);m.locked=x.getInt(4)!=0;m.pending=x.getInt(5)==130;m.location=x.getString(6);m.transaction=x.getString(7);m.subscription=x.getInt(8);return m;}
 private static String addresses(Context c,long id,boolean incoming){List<String> values=new ArrayList<>();try(Cursor a=c.getContentResolver().query(Uri.parse("content://mms/"+id+"/addr"),new String[]{"address","type"},null,null,"_id ASC")){if(a!=null)while(a.moveToNext()){String value=a.getString(0);if(a.getInt(1)==(incoming?137:151)&&value!=null&&!"insert-address-token".equals(value))values.add(value);}}return values.isEmpty()?"Unknown":android.text.TextUtils.join(",",values);}
 static long providerId(long id,boolean mms){if(id<=0||id>(Long.MAX_VALUE-1)/2)throw new IllegalArgumentException("Invalid provider message id");return -(id*2+(mms?1:0));}
 static Uri uri(SmsRepository.Message m){return ContentUris.withAppendedId(m.mms?Telephony.Mms.CONTENT_URI:Telephony.Sms.CONTENT_URI,(-m.id)/2);}
 static void update(Context c,SmsRepository.Message m,String column,int value){if(!SmsRole.isDefault(c))throw new SecurityException("Set SimpleSMS as default to change shared messages");ContentValues values=new ContentValues();values.put(column,value);c.getContentResolver().update(uri(m),values,null,null);}
 static void delete(Context c,SmsRepository.Message m){if(!SmsRole.isDefault(c))throw new SecurityException("Set SimpleSMS as default to delete shared messages");c.getContentResolver().delete(uri(m),"locked=0",null);}
 public static byte[] attachment(Context c,SmsRepository.Part part)throws Exception{if(!part.shared)return Vault.checkedAttachment(c,part.data,part.digest);Uri uri=Uri.parse(part.data);if(!"content".equals(uri.getScheme())||!"mms".equals(uri.getAuthority()))throw new java.io.IOException("Invalid shared media URI");try(java.io.InputStream in=c.getContentResolver().openInputStream(uri)){return MmsTransport.readBounded(in,10*MmsTransport.MAX_BYTES);}}
}
