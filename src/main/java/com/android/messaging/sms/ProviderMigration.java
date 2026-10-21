package com.android.messaging.sms;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.util.Base64;
import com.android.messaging.security.Vault;
import org.json.*;
import java.io.*;
import java.util.*;
/** Explicit, per-message move. Never deletes an original before decrypting the stored copy. */
public final class ProviderMigration {
 private ProviderMigration(){}
 private static final String[] SMS={"_id","address","body","date","type","read","status","locked","thread_id"};
 private static final String[] MMS={"_id","date","msg_box","read","locked","m_type","ct_l","tr_id","sub_id","sub","sub_cs","thread_id"};
 public static void confirm(Activity a){new AlertDialog.Builder(a).setTitle("Move shared messages into SimpleSMS?").setMessage("Copies existing SMS/MMS into encrypted private storage, verifies each copy, then removes that original from Android's shared database. Other messaging apps will no longer see moved messages. Locked originals and active sends are skipped. There is no backup or undo. Existing app drafts/settings are encrypted separately on unlock.").setPositiveButton("Move messages",(d,w)->{ProgressDialog progress=ProgressDialog.show(a,"Moving messages","Keep SimpleSMS open and the device unlocked",true,false);new Thread(()->{String text;try{text=move(a);}catch(Exception e){text="Move stopped: "+e.getMessage()+". Originals not yet verified remain in shared storage.";}String result=text;a.runOnUiThread(()->{progress.dismiss();new AlertDialog.Builder(a).setTitle("Message move").setMessage(result).setPositiveButton("OK",null).show();});},"private-message-move").start();}).setNegativeButton("Cancel",null).show();}
 private static String move(Context c)throws Exception{
  Vault.require();if(!SmsRole.isDefault(c))throw new SecurityException("Set SimpleSMS as the default SMS app first");int moved=0,kept=0;Map<String,Long> prior=new HashMap<>();for(SmsRepository.Message m:SmsRepository.messages(c,-1))if(!m.shared&&m.source!=null)prior.put(m.source,m.id);
  for(boolean mms:new boolean[]{false,true}){Uri base=mms?Uri.parse("content://mms"):Telephony.Sms.CONTENT_URI;String[] projection=mms?MMS:SMS;List<Long> ids=new ArrayList<>();try(Cursor rows=c.getContentResolver().query(base,new String[]{"_id"},null,null,"_id ASC")){if(rows!=null)while(rows.moveToNext())ids.add(rows.getLong(0));}
   for(long id:ids){Vault.require();Uri row=ContentUris.withAppendedId(base,id);JSONObject record;String originalPayload;String before;String selection;String[] selectionArgs;
    try(Cursor x=c.getContentResolver().query(row,projection,null,null,null)){if(x==null||!x.moveToFirst())continue;if(x.getInt(mms?4:7)!=0||x.getInt(mms?2:4)==4||(!mms&&x.getInt(4)==6)){kept++;continue;}StringBuilder where=new StringBuilder();List<String> args=new ArrayList<>();for(int n=0;n<projection.length;n++){if(n>0)where.append(" AND ");where.append(projection[n]);if(x.isNull(n))where.append(" IS NULL");else{where.append("=?");args.add(x.getString(n));}}selection=where.toString();selectionArgs=args.toArray(new String[0]);before=signature(x);record=mms?mms(c,x):sms(x);originalPayload=canonical(record);}
    long oldThread=record.optLong("legacy_thread",-1),newThread=Vault.thread(record.optString("address"));
    if(oldThread>=0){if(com.android.messaging.util.AppPrefs.isArchived(c,oldThread))com.android.messaging.util.AppPrefs.archive(c,newThread,true);String oldDraft=com.android.messaging.util.AppPrefs.draftKey(oldThread,record.optString("address"));String newDraft=com.android.messaging.util.AppPrefs.draftKey(newThread,record.optString("address"));String draft=com.android.messaging.util.AppPrefs.p(c).getString(oldDraft,"");if(!draft.isEmpty()&&com.android.messaging.util.AppPrefs.p(c).getString(newDraft,"").isEmpty())com.android.messaging.util.AppPrefs.p(c).edit().putString(newDraft,draft).remove(oldDraft).apply();}
    String source=(mms?"mms:":"sms:")+id;record.put("source",source);record.put("source_signature",before);long stored=prior.containsKey(source)?prior.get(source):SmsRepository.store(c,record);
    // A full read/authentication of ciphertext must succeed before the provider delete.
    JSONObject verified=Vault.verify(c,stored);
    if(!before.equals(verified.optString("source_signature"))){kept++;continue;}
    JSONObject restored=stripSource(verified);JSONArray verifiedParts=restored.optJSONArray("parts");
    if(verifiedParts!=null)for(int p=0;p<verifiedParts.length();p++){JSONObject part=verifiedParts.getJSONObject(p);byte[] bytes=Vault.checkedAttachment(c,part.getString("attachment"),part.getString("digest"));try{part.put("data",Base64.encodeToString(bytes,Base64.NO_WRAP));part.remove("attachment");part.remove("digest");}finally{Arrays.fill(bytes,(byte)0);}}
    if(!canonical(restored).equals(originalPayload)){kept++;continue;}
    try(Cursor current=c.getContentResolver().query(row,projection,null,null,null)){if(current==null||!current.moveToFirst())continue;if(!signature(current).equals(before)){kept++;continue;}if(mms&&!canonical(mms(c,current)).equals(originalPayload)){kept++;continue;}}
    int count=c.getContentResolver().delete(row,selection,selectionArgs);if(count>0)moved++;else kept++;
   }
  }return "Moved "+moved+" messages. Kept "+kept+" locked, changed or unverified originals in shared storage.";
 }
 /** Only messages created under the explicit private-traffic policy are eligible. */
 public static void cleanNew(Context c,long stored)throws Exception{
  if(!Vault.unlocked()||!SmsRole.isDefault(c)||!SmsRole.hasCore(c))return;JSONObject encrypted=Vault.verify(c,stored);if(!encrypted.optBoolean("private_transport"))return;
  boolean mms=encrypted.optBoolean("mms");Uri base=mms?Telephony.Mms.CONTENT_URI:Telephony.Sms.CONTENT_URI;String selection;String[] args;String source=encrypted.optString("source","");
  if(source.startsWith(mms?"mms:":"sms:")){selection="_id=?";args=new String[]{source.substring(4)};}
  else if(!mms){selection="date=? AND address=? AND body=? AND sub_id=?";args=new String[]{""+encrypted.optLong("date"),encrypted.optString("address"),encrypted.optString("body"),""+encrypted.optInt("subscription",-1)};}
  else if(!encrypted.optString("location","").isEmpty()){selection="ct_l=? AND sub_id=?";args=new String[]{encrypted.optString("location"),""+encrypted.optInt("subscription",-1)};}
  else if(!encrypted.optString("transaction","").isEmpty()){selection="tr_id=? AND sub_id=?";args=new String[]{encrypted.optString("transaction"),""+encrypted.optInt("subscription",-1)};}
  else{done(c,stored);return;}
  List<Long> ids=new ArrayList<>();try(Cursor x=c.getContentResolver().query(base,new String[]{"_id"},selection,args,null)){if(x!=null)while(x.moveToNext())ids.add(x.getLong(0));}
  if(ids.isEmpty()){done(c,stored);return;}if(ids.size()!=1)return;Uri row=ContentUris.withAppendedId(base,ids.get(0));String[] projection=mms?MMS:SMS;
  JSONObject original;String before;String where;String[] whereArgs;
  try(Cursor x=c.getContentResolver().query(row,projection,null,null,null)){if(x==null||!x.moveToFirst())return;if(x.getInt(mms?4:7)!=0||x.getInt(mms?2:4)==3||x.getInt(mms?2:4)==4||(!mms&&x.getInt(4)==6))return;before=signature(x);original=mms?mms(c,x):sms(x);if(!sameNew(encrypted,original))return;StringBuilder clause=new StringBuilder();List<String> values=new ArrayList<>();for(int n=0;n<projection.length;n++){if(n>0)clause.append(" AND ");clause.append(projection[n]);if(x.isNull(n))clause.append(" IS NULL");else{clause.append("=?");values.add(x.getString(n));}}where=clause.toString();whereArgs=values.toArray(new String[0]);}
  // Fully authenticate the persisted record and every private media file before any shared deletion.
  JSONObject verified=Vault.verify(c,stored);if(!sameNew(verified,original))return;JSONArray parts=verified.optJSONArray("parts");if(parts!=null)for(int n=0;n<parts.length();n++){JSONObject part=parts.getJSONObject(n);byte[] bytes=Vault.checkedAttachment(c,part.getString("attachment"),part.getString("digest"));Arrays.fill(bytes,(byte)0);}
  try(Cursor x=c.getContentResolver().query(row,projection,null,null,null)){if(x==null||!x.moveToFirst()||!before.equals(signature(x)))return;if(mms&&!canonical(original).equals(canonical(mms(c,x))))return;}
  if(c.getContentResolver().delete(row,where,whereArgs)==1)done(c,stored);
 }
 private static void done(Context c,long id)throws Exception{Vault.append(c,new JSONObject().put("kind","cleanup_done").put("message",id));}
 static boolean sameNew(JSONObject copy,JSONObject shared)throws Exception{
  boolean mms=copy.optBoolean("mms");if(mms!=shared.optBoolean("mms")||(copy.optInt("type",1)==1)!=(shared.optInt("type",1)==1)||copy.optBoolean("pending")!=shared.optBoolean("pending"))return false;
  if((mms?copy.optLong("date")/1000:copy.optLong("date"))!=(mms?shared.optLong("date")/1000:shared.optLong("date"))||!copy.optString("address").equals(shared.optString("address"))||!copy.optString("body").equals(shared.optString("body")))return false;
  JSONArray a=copy.optJSONArray("parts"),b=shared.optJSONArray("parts");int count=a==null?0:a.length();if(count!=(b==null?0:b.length()))return false;
  for(int n=0;n<count;n++){JSONObject left=a.getJSONObject(n),right=b.getJSONObject(n);if(!left.optString("mime").equals(right.optString("mime")))return false;byte[] bytes=Base64.decode(right.getString("data"),Base64.NO_WRAP);try{String digest=Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(bytes),Base64.NO_WRAP);if(!digest.equals(left.optString("digest")))return false;}finally{Arrays.fill(bytes,(byte)0);}}
  return true;
 }
 private static JSONObject stripSource(JSONObject o)throws Exception{JSONObject copy=new JSONObject(o.toString());copy.remove("source");copy.remove("source_signature");copy.remove("kind");copy.remove("_id");return copy;}
 private static JSONObject sms(Cursor x)throws Exception{return new JSONObject().put("legacy_thread",x.getLong(8)).put("address",x.isNull(1)?"Unknown":x.getString(1)).put("body",x.isNull(2)?"":x.getString(2)).put("date",x.getLong(3)).put("provider_date",x.getString(3)).put("type",x.getInt(4)==4||x.getInt(4)==6?5:x.getInt(4)).put("read",x.getInt(5)).put("status",x.getInt(6));}
 private static JSONObject mms(Context c,Cursor x)throws Exception{
  long id=x.getLong(0);boolean incoming=x.getInt(2)==1;List<String> dest=new ArrayList<>();JSONArray addresses=new JSONArray();try(Cursor a=c.getContentResolver().query(Uri.parse("content://mms/"+id+"/addr"),new String[]{"address","type","charset"},null,null,"_id ASC")){if(a!=null)while(a.moveToNext()){String address=a.getString(0);addresses.put(new JSONObject().put("address",address).put("type",a.getInt(1)).put("charset",a.getInt(2)));if(a.getInt(1)==(incoming?137:151)&&address!=null&&!"insert-address-token".equals(address))dest.add(address);}}
  JSONObject record=new JSONObject().put("legacy_thread",x.getLong(11)).put("mms",true).put("address",dest.isEmpty()?"Unknown":android.text.TextUtils.join(",",dest)).put("addresses",addresses).put("date",x.getLong(1)*1000).put("provider_date",x.getString(1)).put("type",x.getInt(2)==4?5:x.getInt(2)).put("read",x.getInt(3)).put("pending",x.getInt(5)==130).put("location",x.getString(6)).put("transaction",x.getString(7)).put("subscription",x.getInt(8));
  StringBuilder body=new StringBuilder();if(!x.isNull(9)){String subject=new com.google.android.mms.pdu_alt.EncodedStringValue(x.getInt(10),x.getString(9).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)).getString();body.append("Subject: ").append(subject);}JSONArray parts=new JSONArray();
  try(Cursor p=c.getContentResolver().query(Uri.parse("content://mms/"+id+"/part"),new String[]{"_id","ct","name","text"},null,null,"seq ASC, _id ASC")){if(p!=null)while(p.moveToNext()){String mime=p.getString(1);if("text/plain".equals(mime)){if(body.length()>0)body.append('\n');body.append(p.isNull(3)?"":p.getString(3));}else if(!"application/smil".equals(mime))try(InputStream in=c.getContentResolver().openInputStream(Uri.parse("content://mms/part/"+p.getLong(0)))){parts.put(new JSONObject().put("mime",mime).put("name",p.isNull(2)?"":p.getString(2)).put("data",Base64.encodeToString(MmsTransport.readBounded(in,10*MmsTransport.MAX_BYTES),Base64.NO_WRAP)));}}}
  return record.put("body",body.toString()).put("parts",parts);
 }
 private static String signature(Cursor c){StringBuilder b=new StringBuilder();for(int i=0;i<c.getColumnCount();i++)b.append(c.isNull(i)?"<null>":c.getString(i)).append('\u0000');return b.toString();}
 private static String canonical(Object value)throws Exception{if(value instanceof JSONObject){JSONObject o=(JSONObject)value;List<String> keys=new ArrayList<>();Iterator<String> it=o.keys();while(it.hasNext())keys.add(it.next());Collections.sort(keys);StringBuilder b=new StringBuilder("{");for(String key:keys)b.append(JSONObject.quote(key)).append(':').append(canonical(o.get(key))).append(',');return b.append('}').toString();}if(value instanceof JSONArray){JSONArray a=(JSONArray)value;StringBuilder b=new StringBuilder("[");for(int i=0;i<a.length();i++)b.append(canonical(a.get(i))).append(',');return b.append(']').toString();}return value instanceof String?JSONObject.quote((String)value):String.valueOf(value);}
}
