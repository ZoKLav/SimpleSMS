package com.android.messaging.sms;
import android.content.Context;
import android.net.Uri;
import com.android.messaging.security.Vault;
import com.android.messaging.util.*;
import org.json.*;
import java.util.*;
/** Private encrypted messages and live shared SMS/MMS use one chronological view. */
public final class SmsRepository {
 public static final class Conversation{public long threadId,messageId,date;public String address,title,snippet;public int type,unread;}
 public static final class Part{public String mime,name,data,digest;public boolean shared;}
 public static final class Message{public long id,date,dateSent,threadId,providerThread=-1;public String address,body,location,transaction,source;public int type,status=-1,error,subscription=-1,read;public boolean mms,pending,locked,shared;public final List<Part> parts=new ArrayList<>();}
 private SmsRepository(){}
 public static synchronized long store(Context c,JSONObject record)throws Exception{
  List<String> created=new ArrayList<>();try{JSONArray parts=record.optJSONArray("parts");if(parts!=null)for(int i=0;i<parts.length();i++){JSONObject part=parts.getJSONObject(i);if(part.has("data")){byte[] bytes=android.util.Base64.decode(part.getString("data"),android.util.Base64.NO_WRAP);try{String name=Vault.saveAttachment(c,bytes);created.add(name);part.put("attachment",name).put("digest",android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(bytes),android.util.Base64.NO_WRAP));part.remove("data");}finally{Arrays.fill(bytes,(byte)0);}}}record.put("kind","message");return Vault.append(c,record);}catch(Exception e){for(String name:created)try{Vault.eraseAttachment(c,name);}catch(Exception ignored){}throw e;}
 }
 private static volatile PrivateState cached;
 public static void clearCache(){cached=null;}
 private static final class PrivateState{
  long revision;List<Message> all;Map<Long,List<Message>> threads=new HashMap<>();Map<Long,Message> ids=new HashMap<>();Map<String,JSONObject> sources=new HashMap<>();Map<Long,JSONObject> records;List<JSONObject> rows;
 }
 private static PrivateState privateState(Context c)throws Exception{
  for(;;){long expected=Vault.revision();PrivateState ready=cached;if(ready!=null&&ready.revision==expected)return ready;
   List<JSONObject> rows=Vault.recordsOfKinds(c,"message","event","cleanup_done","notification_reply");long thread=-1;
  Map<Long,Set<Integer>> sent=new HashMap<>(),delivered=new HashMap<>();
  Map<Long,JSONObject> records=new LinkedHashMap<>();for(JSONObject r:rows){
   if("message".equals(r.optString("kind")))records.put(r.getLong("_id"),r);
   else if("event".equals(r.optString("kind"))){JSONObject m=records.get(r.optLong("message"));if(m!=null){JSONObject flags=r.getJSONObject("flags");long id=r.optLong("message");
    if(flags.has("sent_part")){
     if(flags.optInt("part_result")!=android.app.Activity.RESULT_OK){m.put("type",5);m.put("error",flags.optInt("part_result"));}
     else {Set<Integer> done=sent.get(id);if(done==null){done=new HashSet<>();sent.put(id,done);}done.add(flags.getInt("sent_part"));if(done.size()>=flags.optInt("part_count",1)&&m.optInt("type")!=5)m.put("type",2);}
    }
    if(flags.has("delivery_part")){int status=flags.optInt("delivery_status",-1);if(status>=64)m.put("status",64);else if(status>=0&&status<32){Set<Integer> done=delivered.get(id);if(done==null){done=new HashSet<>();delivered.put(id,done);}done.add(flags.getInt("delivery_part"));if(done.size()>=flags.optInt("part_count",1)&&m.optInt("status",-1)<64)m.put("status",0);}}
    Iterator<String> keys=flags.keys();while(keys.hasNext()){String k=keys.next();m.put(k,flags.get(k));}}}
  }
  Set<String> completed=new HashSet<>(),pendingSeen=new HashSet<>();for(JSONObject r:records.values())if(r.optBoolean("mms")&&!r.optBoolean("pending")&&r.has("location"))completed.add(r.optString("location")+"|"+r.optInt("subscription",-1));
  List<Message> out=new ArrayList<>();for(JSONObject r:records.values())if(!r.optBoolean("deleted")){
   if(r.optBoolean("pending")){String key=r.optString("location")+"|"+r.optInt("subscription",-1);if(completed.contains(key)||!pendingSeen.add(key))continue;}
   JSONObject parent=records.get(r.optLong("parent",-1));
   Message m=new Message();m.id=r.getLong("_id");m.address=r.optString("address","Unknown");m.threadId=Vault.thread(m.address);if(thread>=0&&m.threadId!=thread)continue;
   m.body=r.optString("body","");m.date=r.optLong("date");m.dateSent=r.optLong("date_sent");m.type=r.optInt("type",1);m.status=r.optInt("status",-1);m.error=r.optInt("error");m.read=r.optInt("read");m.mms=r.optBoolean("mms");m.pending=r.optBoolean("pending");m.locked=r.has("locked")?r.optBoolean("locked"):parent!=null&&parent.optBoolean("locked");if(parent!=null&&parent.optInt("read")==1)m.read=1;m.location=r.optString("location",null);m.transaction=r.optString("transaction",null);m.source=r.optString("source",null);m.subscription=r.optInt("subscription",-1);
   JSONArray parts=r.optJSONArray("parts");if(parts!=null)for(int i=0;i<parts.length();i++){JSONObject p=parts.getJSONObject(i);Part part=new Part();part.mime=p.optString("mime","application/octet-stream");part.name=p.optString("name","attachment");part.data=p.optString("attachment","");part.digest=p.optString("digest","");m.parts.add(part);}out.add(m);
  }
   Collections.sort(out,(x,y)->{int date=Long.compare(x.date,y.date);return date!=0?date:Long.compare(x.id,y.id);});
   PrivateState state=new PrivateState();state.revision=expected;state.rows=rows;state.records=records;state.all=Collections.unmodifiableList(out);
   for(Message m:out){state.ids.put(m.id,m);List<Message> list=state.threads.get(m.threadId);if(list==null){list=new ArrayList<>();state.threads.put(m.threadId,list);}list.add(m);}
   for(JSONObject r:records.values())if(!r.optBoolean("deleted")&&r.has("source"))state.sources.put(r.optString("source"),r);
   synchronized(Vault.class){Vault.require();if(Vault.revision()!=expected)continue;cached=state;return state;}
  }
 }
 public static List<Message> messages(Context c,long thread)throws Exception{
  PrivateState state=privateState(c);List<Message> selected=thread<0?state.all:state.threads.get(thread);List<Message> out=selected==null?new ArrayList<>():new ArrayList<>(selected);
  out.addAll(SharedMessages.read(c,thread,state.sources));Set<String> retrieved=new HashSet<>();for(Message m:out)if(m.mms&&!m.pending&&m.location!=null&&!m.location.isEmpty())retrieved.add(m.location+"|"+m.subscription);
  Iterator<Message> pending=out.iterator();while(pending.hasNext()){Message m=pending.next();if(m.pending&&retrieved.contains(m.location+"|"+m.subscription))pending.remove();}
  Collections.sort(out,(x,y)->{int date=Long.compare(x.date,y.date);return date!=0?date:Long.compare(x.id,y.id);});return out;
 }
 public static List<Conversation> conversations(Context c,boolean archived)throws Exception{Map<Long,Conversation> map=new HashMap<>();for(Message m:messages(c,-1)){Conversation q=map.get(m.threadId);if(q==null){q=new Conversation();q.threadId=m.threadId;map.put(m.threadId,q);}q.messageId=m.id;q.date=m.date;q.address=m.address;q.type=m.type;q.snippet=m.mms&&AppPrefs.hideMms(c)?"MMS · Tap to display message":m.pending?"MMS ready to download":m.body+(m.parts.isEmpty()?"":" [Attachment]");if(m.type==1&&m.read==0)q.unread++;}List<Conversation> out=new ArrayList<>();for(Conversation q:map.values())if(AppPrefs.isArchived(c,q.threadId)==archived){q.title=ContactResolver.name(c,q.address);out.add(q);}Collections.sort(out,(a,b)->Long.compare(b.date,a.date));return out;}
 public static String address(Context c,long thread)throws Exception{for(Message m:messages(c,thread))return m.address;return null;}
 public static Message message(Context c,long id)throws Exception{PrivateState state=privateState(c);if(id>0)return state.ids.get(id);List<Message> rows=SharedMessages.readOne(c,id,state.sources);return rows.isEmpty()?null:rows.get(0);}
 public static void markRead(Context c,long thread)throws Exception{markRead(c,messages(c,thread));}
 public static void markRead(Context c,List<Message> messages)throws Exception{Vault.require();boolean defaultApp=SmsRole.isDefault(c);for(Message m:messages)if(m.read==0){if(m.shared){if(defaultApp)SharedMessages.update(c,m,"read",1);}else Vault.event(c,m.id,new JSONObject().put("read",1));}}
 public static void deleteThread(Context c,long thread)throws Exception{
  List<Message> visible=messages(c,thread);List<Message> shared=new ArrayList<>();Set<Long> ids=new HashSet<>();
  for(Message m:visible)if(m.shared&&!m.locked)shared.add(m);
  SharedMessages.deleteAll(c,shared);
  synchronized(Vault.class){PrivateState state=privateState(c);for(JSONObject record:state.records.values())if(Vault.thread(record.optString("address","Unknown"))==thread&&!record.optBoolean("deleted")&&!locked(record,state.records))ids.add(record.getLong("_id"));erasePrivate(c,state,ids);}
  AppPrefs.archive(c,thread,false);AppPrefs.p(c).edit().remove(AppPrefs.draftKey(thread,null)).apply();
 }
 private static boolean locked(JSONObject record,Map<Long,JSONObject> records){JSONObject parent=records.get(record.optLong("parent",-1));return record.has("locked")?record.optBoolean("locked"):parent!=null&&parent.optBoolean("locked");}
 private static void erasePrivate(Context c,PrivateState state,Set<Long> ids)throws Exception{
  if(ids.isEmpty())return;Set<Long> rows=new HashSet<>(ids);List<String> attachments=new ArrayList<>();
  for(long id:ids){JSONObject record=state.records.get(id);if(record==null)continue;JSONArray parts=record.optJSONArray("parts");if(parts!=null)for(int i=0;i<parts.length();i++)attachments.add(parts.getJSONObject(i).optString("attachment"));}
  for(JSONObject row:state.rows)if(ids.contains(row.optLong("message",-1)))rows.add(row.getLong("_id"));
  Vault.eraseMany(c,rows);for(String attachment:attachments)Vault.eraseAttachment(c,attachment);
 }
 public static void deleteMessage(Context c,long id)throws Exception{
  if(id<0){Message shared=message(c,id);if(shared==null)return;if(shared.locked)throw new IllegalStateException("Unlock this message before deleting it");SharedMessages.delete(c,shared);return;}
  synchronized(Vault.class){PrivateState state=privateState(c);JSONObject record=state.records.get(id);if(record==null||record.optBoolean("deleted"))return;if(locked(record,state.records))throw new IllegalStateException("Unlock this message before deleting it");erasePrivate(c,state,Collections.singleton(id));}
 }
 public static void setLocked(Context c,Message m,boolean locked)throws Exception{if(m.shared)SharedMessages.update(c,m,"locked",locked?1:0);else Vault.event(c,m.id,new JSONObject().put("locked",locked));}
}
