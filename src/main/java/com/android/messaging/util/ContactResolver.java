package com.android.messaging.util;
import android.Manifest;import android.app.Activity;import android.content.*;import android.content.pm.PackageManager;import android.database.*;import android.net.Uri;import android.os.*;import android.provider.ContactsContract;import android.util.LruCache;import android.widget.Toast;import com.android.messaging.security.Vault;import java.util.*;
public final class ContactResolver {
 private static final LruCache<String,String> CACHE=new LruCache<>(512);private static final Set<String>PENDING=new HashSet<>();private static ContentObserver observer;private static int generation;private ContactResolver(){}
 public static synchronized void clear(){CACHE.evictAll();PENDING.clear();generation++;}
 public static String name(Context context,String address){
  if(address==null||address.trim().isEmpty())return "Unknown";if(context.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return address;Context app=context.getApplicationContext();int expected;
  synchronized(ContactResolver.class){
   if(observer==null){observer=new ContentObserver(new Handler(Looper.getMainLooper())){public void onChange(boolean self){clear();app.getContentResolver().notifyChange(Vault.changes(app),null);}};try{app.getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI,true,observer);}catch(SecurityException unavailable){observer=null;return address;}}
   String cached=CACHE.get(address);if(cached!=null)return cached;expected=generation;
   if(Looper.myLooper()==Looper.getMainLooper()){if(PENDING.add(address))Workers.CONTACTS.execute(()->resolve(app,address,expected,true));return address;}
  }
  return resolve(app,address,expected,false);
 }
 private static String resolve(Context app,String address,int expected,boolean notify){
  String name=address;
  try(Cursor cursor=app.getContentResolver().query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(address)),new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst()&&!cursor.isNull(0)&&!cursor.getString(0).isEmpty())name=cursor.getString(0);}catch(RuntimeException unavailable){}
  boolean unlocked=Vault.unlocked();synchronized(ContactResolver.class){if(generation!=expected||!unlocked)return address;CACHE.put(address,name);PENDING.remove(address);}
  if(notify)app.getContentResolver().notifyChange(Vault.changes(app),null);return name;
 }
 public static boolean singleNumber(String address){if(address==null||!address.trim().matches("[+0-9() .\\-]+"))return false;return android.telephony.PhoneNumberUtils.isGlobalPhoneNumber(android.telephony.PhoneNumberUtils.normalizeNumber(address));}
 public static void addToContacts(Activity activity,String address){if(!singleNumber(address))return;try{activity.startActivity(new Intent(Intent.ACTION_INSERT_OR_EDIT).setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE).putExtra(ContactsContract.Intents.Insert.PHONE,address));}catch(ActivityNotFoundException unavailable){Toast.makeText(activity,"No contacts editor available",Toast.LENGTH_LONG).show();}}
}
