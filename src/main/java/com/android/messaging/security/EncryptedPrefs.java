package com.android.messaging.security;
import android.content.*;
import android.app.Application;
import org.json.*;
import java.util.*;
/** Preferences and drafts never go to a plaintext SharedPreferences XML file. */
final class EncryptedPrefs implements SharedPreferences {
 private long storedId=-1;private final Application application;private final Map<String,Object> values=new HashMap<>();private final Set<OnSharedPreferenceChangeListener> listeners=Collections.newSetFromMap(new WeakHashMap<>());
 private static final EncryptedPrefs LOCKED=new EncryptedPrefs();
 private EncryptedPrefs(){application=null;}
 static SharedPreferences locked(){return LOCKED;}
 EncryptedPrefs(Context c)throws Exception{application=(Application)c.getApplicationContext();for(JSONObject r:Vault.recordsOfKinds(c,"prefs"))if("prefs".equals(r.optString("kind"))){storedId=r.getLong("_id");values.clear();JSONObject json=r.getJSONObject("values");Iterator<String> keys=json.keys();while(keys.hasNext()){String k=keys.next();Object value=json.get(k);if(value instanceof JSONArray){Set<String> s=new HashSet<>();JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)s.add(a.getString(i));value=s;}values.put(k,value);}}}
 synchronized void forget(){values.clear();listeners.clear();}
 void importLegacy(Map<String,?> legacy){Editor e=edit();for(Map.Entry<String,?> item:legacy.entrySet()){Object v=item.getValue();if(v instanceof String)e.putString(item.getKey(),(String)v);else if(v instanceof Boolean)e.putBoolean(item.getKey(),(Boolean)v);else if(v instanceof Integer)e.putInt(item.getKey(),(Integer)v);else if(v instanceof Long)e.putLong(item.getKey(),(Long)v);else if(v instanceof Float)e.putFloat(item.getKey(),(Float)v);else if(v instanceof Set)e.putStringSet(item.getKey(),new HashSet<>((Set<String>)v));}if(!e.commit())throw new IllegalStateException("Legacy preference encryption failed");}
 @Override public synchronized Map<String,?> getAll(){return new HashMap<>(values);}
 @Override public synchronized boolean contains(String k){return values.containsKey(k);}
 @Override public synchronized String getString(String k,String def){Object v=values.get(k);return v instanceof String?(String)v:def;}
 @Override public synchronized Set<String> getStringSet(String k,Set<String> def){Object v=values.get(k);return v instanceof Set?new HashSet<>((Set<String>)v):def==null?null:new HashSet<>(def);}
 @Override public synchronized int getInt(String k,int def){Object v=values.get(k);return v instanceof Number?((Number)v).intValue():def;}
 @Override public synchronized long getLong(String k,long def){Object v=values.get(k);return v instanceof Number?((Number)v).longValue():def;}
 @Override public synchronized float getFloat(String k,float def){Object v=values.get(k);return v instanceof Number?((Number)v).floatValue():def;}
 @Override public synchronized boolean getBoolean(String k,boolean def){Object v=values.get(k);return v instanceof Boolean?(Boolean)v:def;}
 @Override public synchronized void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listeners.add(l);}
 @Override public synchronized void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listeners.remove(l);}
 @Override public Editor edit(){return new Edit();}
 private final class Edit implements Editor {
  private final Map<String,Object> changed=new HashMap<>();private boolean clear;
  public Editor putString(String k,String v){changed.put(k,v);return this;}public Editor putStringSet(String k,Set<String> v){changed.put(k,v==null?null:new HashSet<>(v));return this;}public Editor putInt(String k,int v){changed.put(k,v);return this;}public Editor putLong(String k,long v){changed.put(k,v);return this;}public Editor putFloat(String k,float v){changed.put(k,v);return this;}public Editor putBoolean(String k,boolean v){changed.put(k,v);return this;}public Editor remove(String k){changed.put(k,null);return this;}public Editor clear(){clear=true;return this;}
  public boolean commit(){
   Set<String> keys;
   synchronized(Vault.class){synchronized(EncryptedPrefs.this){
    if(application==null||!Vault.unlocked()||Vault.preferences(application)!=EncryptedPrefs.this)return false;
    Map<String,Object> next=new HashMap<>(values);if(clear)next.clear();for(Map.Entry<String,Object> e:changed.entrySet())if(e.getValue()==null)next.remove(e.getKey());else next.put(e.getKey(),e.getValue());if(next.equals(values))return true;
    try{JSONObject json=new JSONObject();for(Map.Entry<String,Object> e:next.entrySet())json.put(e.getKey(),e.getValue() instanceof Set?new JSONArray((Set<?>)e.getValue()):e.getValue());long keep=Vault.append(application,new JSONObject().put("kind","prefs").put("values",json));long old=storedId;storedId=keep;if(old>=0)try{Vault.erase(application,old);}catch(Exception ignored){}keys=new HashSet<>(changed.keySet());if(clear)keys.addAll(values.keySet());values.clear();values.putAll(next);}catch(Exception failure){return false;}
   }}
   List<java.lang.ref.WeakReference<OnSharedPreferenceChangeListener>> pending=new ArrayList<>();synchronized(EncryptedPrefs.this){for(OnSharedPreferenceChangeListener listener:listeners)pending.add(new java.lang.ref.WeakReference<>(listener));}
   new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{for(java.lang.ref.WeakReference<OnSharedPreferenceChangeListener> reference:pending){OnSharedPreferenceChangeListener listener=reference.get();synchronized(EncryptedPrefs.this){if(listener==null||!listeners.contains(listener))continue;}for(String key:keys)listener.onSharedPreferenceChanged(EncryptedPrefs.this,key);}});return true;
  }
  public void apply(){if(!commit()&&application!=null)throw new IllegalStateException("Unlock the app to save encrypted preferences");}
 }
}
