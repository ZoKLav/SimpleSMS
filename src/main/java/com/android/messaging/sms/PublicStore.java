package com.android.messaging.sms;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.util.*;
/** Normal default-SMS-app persistence, selected only when private storage is off. */
public final class PublicStore {
 private PublicStore(){}
 static ContentValues header(JSONObject r){boolean mms=r.optBoolean("mms");ContentValues v=new ContentValues();v.put("date",mms?r.optLong("date")/1000:r.optLong("date"));v.put(mms?"msg_box":"type",r.optInt("type",1));v.put("read",r.optInt("read"));v.put("seen",r.optInt("read"));v.put("locked",r.optBoolean("locked")?1:0);v.put("sub_id",r.optInt("subscription",-1));
  if(!mms){v.put("address",r.optString("address","Unknown"));v.put("body",r.optString("body",""));v.put("date_sent",r.optLong("date_sent"));v.put("status",r.optInt("status",-1));}
  else{v.put("m_type",r.optBoolean("pending")?130:r.optInt("type",1)==1?132:128);v.put("v",18);v.put("ct_t","application/vnd.wap.multipart.related");v.put("text_only",r.optJSONArray("parts")==null||r.optJSONArray("parts").length()==0?1:0);if(r.has("location"))v.put("ct_l",r.optString("location"));if(r.has("transaction"))v.put("tr_id",r.optString("transaction"));}
  return v;
 }
 public static long write(Context c,JSONObject r)throws Exception{
  if(!SmsRole.isDefault(c))throw new SecurityException("Set SimpleSMS as default to save shared messages");boolean mms=r.optBoolean("mms");ContentValues values=header(r);Set<String> recipients=new HashSet<>();for(String address:r.optString("address","Unknown").split("[,;]"))if(!address.trim().isEmpty()&&!"Unknown".equals(address))recipients.add(address.trim());if(!recipients.isEmpty())values.put("thread_id",Telephony.Threads.getOrCreateThreadId(c,recipients));Uri row=c.getContentResolver().insert(mms?Telephony.Mms.CONTENT_URI:Telephony.Sms.CONTENT_URI,values);if(row==null)throw new IOException("Android did not save the message");
  try{if(mms){long id=ContentUris.parseId(row);JSONArray addresses=r.optJSONArray("addresses");if(addresses==null||addresses.length()==0){addresses=new JSONArray();for(String address:r.optString("address","Unknown").split("[,;]"))addresses.put(new JSONObject().put("address",address.trim()).put("type",r.optInt("type",1)==1?137:151));}
    for(int i=0;i<addresses.length();i++){JSONObject a=addresses.getJSONObject(i);ContentValues v=new ContentValues();v.put("address",a.getString("address"));v.put("type",a.getInt("type"));v.put("charset",a.optInt("charset",106));if(c.getContentResolver().insert(Uri.parse("content://mms/"+id+"/addr"),v)==null)throw new IOException("Could not save MMS recipient");}
    int seq=0;String body=r.optString("body","");if(!body.isEmpty())part(c,id,seq++,"text/plain","text.txt",body,null);
    JSONArray parts=r.optJSONArray("parts");if(parts!=null)for(int i=0;i<parts.length();i++){JSONObject part=parts.getJSONObject(i);byte[] bytes=part.has("data")?Base64.decode(part.getString("data"),Base64.NO_WRAP):com.android.messaging.security.Vault.checkedAttachment(c,part.getString("attachment"),part.getString("digest"));try{part(c,id,seq++,part.optString("mime","application/octet-stream"),part.optString("name","attachment"),null,bytes);}finally{Arrays.fill(bytes,(byte)0);}}
   }return SharedMessages.providerId(ContentUris.parseId(row),mms);
  }catch(Exception failure){try{c.getContentResolver().delete(row,null,null);}catch(Exception cleanup){failure.addSuppressed(cleanup);}throw failure;}
 }
 private static void part(Context c,long id,int seq,String mime,String name,String text,byte[] bytes)throws Exception{ContentValues values=new ContentValues();values.put("seq",seq);values.put("ct",mime);values.put("name",name);values.put("cl",name);values.put("chset",106);if(text!=null)values.put("text",text);Uri part=c.getContentResolver().insert(Uri.parse("content://mms/"+id+"/part"),values);if(part==null)throw new IOException("Could not save MMS part");if(bytes!=null)try(OutputStream out=c.getContentResolver().openOutputStream(part)){if(out==null)throw new IOException("Could not open shared MMS media");out.write(bytes);}}
 static Uri uri(long id){if(id>=-1)throw new IllegalArgumentException("Not a shared message ID");long raw=-id;return ContentUris.withAppendedId((raw&1)==1?Telephony.Mms.CONTENT_URI:Telephony.Sms.CONTENT_URI,raw/2);}
 static void state(Context c,long id,JSONObject flags)throws Exception{if(!SmsRole.isDefault(c))return;boolean mms=((-id)&1)==1;ContentValues v=new ContentValues();if(flags.has("type"))v.put(mms?"msg_box":"type",flags.getInt("type"));if(!mms&&flags.has("status"))v.put("status",flags.getInt("status"));if(v.size()==0)return;
  Uri row=uri(id);String[] columns=v.keySet().toArray(new String[0]);boolean changed=false;try(Cursor x=c.getContentResolver().query(row,columns,null,null,null)){if(x==null||!x.moveToFirst())return;for(int i=0;i<columns.length;i++)if(x.getInt(i)!=v.getAsInteger(columns[i]))changed=true;}if(changed)c.getContentResolver().update(row,v,null,null);
 }
 static void complete(Context c,long parent)throws Exception{if(parent>=0)com.android.messaging.security.Vault.event(c,parent,new JSONObject().put("deleted",true));else if(SmsRole.isDefault(c))c.getContentResolver().delete(uri(parent),"m_type=130 AND locked=0",null);}
}
