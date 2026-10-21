package com.android.messaging.sms;
import android.content.Context;
import com.android.messaging.security.*;
import org.json.*;
import java.util.*;
/** Storage route is captured at message creation, never re-read by later callbacks. */
public final class TransportStore {
 private TransportStore(){}
 public static long store(Context c,JSONObject record)throws Exception{return store(c,record,StorageMode.enabled(c));}
 public static long store(Context c,JSONObject record,boolean privateMode)throws Exception{if(!privateMode)return PublicStore.write(c,record);long id=SmsRepository.store(c,record.put("private_transport",true));if(Vault.unlocked())try{ProviderMigration.cleanNew(c,id);}catch(Exception ignored){/* Verified cleanup retries on next open. Original retained on failure. */}return id;}
 public static void event(Context c,long id,JSONObject flags)throws Exception{if(id>=0){Vault.event(c,id,flags);if(Vault.unlocked())try{ProviderMigration.cleanNew(c,id);}catch(Exception ignored){/* Retry while unlocked; never delete an unverified original. */}}else{Vault.append(c,new JSONObject().put("kind","shared_status").put("message",id).put("flags",flags));if(Vault.unlocked())reconcile(c);}}
 public static void complete(Context c,long parent)throws Exception{PublicStore.complete(c,parent);}
 static JSONObject status(List<JSONObject> events)throws Exception{JSONObject out=new JSONObject();Set<Integer> sent=new HashSet<>(),delivered=new HashSet<>();int sendCount=0,deliveryCount=0;boolean failed=false,deliveryFailed=false;
  for(JSONObject f:events){if(f.has("type"))out.put("type",f.getInt("type"));if(f.has("status"))out.put("status",f.getInt("status"));if(f.has("sent_part")){sendCount=Math.max(sendCount,f.optInt("part_count",1));if(f.optInt("part_result")!=android.app.Activity.RESULT_OK)failed=true;else sent.add(f.getInt("sent_part"));}if(f.has("delivery_part")){deliveryCount=Math.max(deliveryCount,f.optInt("part_count",1));int s=f.optInt("delivery_status",-1);if(s>=64)deliveryFailed=true;else if(s>=0&&s<32)delivered.add(f.getInt("delivery_part"));}}
  if(failed)out.put("type",5);else if(sendCount>0&&sent.size()>=sendCount)out.put("type",2);if(deliveryFailed)out.put("status",64);else if(deliveryCount>0&&delivered.size()>=deliveryCount)out.put("status",0);return out;
 }
 public static synchronized void reconcile(Context c)throws Exception{if(!Vault.unlocked()||!SmsRole.isDefault(c)||!SmsRole.hasCore(c))return;Map<Long,List<JSONObject>> groups=new HashMap<>();List<Long> cleanup=new ArrayList<>();Set<Long> done=new HashSet<>();for(JSONObject r:Vault.records(c)){if("shared_status".equals(r.optString("kind"))){long id=r.getLong("message");List<JSONObject> list=groups.get(id);if(list==null){list=new ArrayList<>();groups.put(id,list);}list.add(r.getJSONObject("flags"));}else if("cleanup_done".equals(r.optString("kind")))done.add(r.getLong("message"));else if(r.optBoolean("private_transport"))cleanup.add(r.getLong("_id"));}for(Map.Entry<Long,List<JSONObject>> e:groups.entrySet())PublicStore.state(c,e.getKey(),status(e.getValue()));for(long id:cleanup)if(!done.contains(id))ProviderMigration.cleanNew(c,id);}
}
