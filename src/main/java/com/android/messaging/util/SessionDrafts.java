package com.android.messaging.util;
import android.content.Context;import android.content.SharedPreferences;import com.android.messaging.security.Vault;import java.util.*;
public final class SessionDrafts {
 private static final Map<String,String> VALUES=new HashMap<>();private SessionDrafts(){}
 public static synchronized void clear(){VALUES.clear();}
 public static boolean remember(Context context,SharedPreferences session,String key,String value){synchronized(Vault.class){if(!Vault.unlocked()||AppPrefs.p(context)!=session)return false;synchronized(SessionDrafts.class){VALUES.put(key,value);}return true;}}
 public static String read(Context context,String key){synchronized(Vault.class){if(!Vault.unlocked())return "";synchronized(SessionDrafts.class){if(VALUES.containsKey(key))return VALUES.get(key);}}return AppPrefs.p(context).getString(key,"");}
 public static synchronized boolean current(String key,String value){return value.equals(VALUES.get(key));}
 public static synchronized void persisted(String key,String value){if(value.equals(VALUES.get(key)))VALUES.remove(key);}
}
