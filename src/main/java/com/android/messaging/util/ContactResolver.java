package com.android.messaging.util;
import android.Manifest;import android.content.Context;import android.content.pm.PackageManager;import android.database.Cursor;import android.net.Uri;import android.provider.ContactsContract;import java.util.concurrent.ConcurrentHashMap;
public final class ContactResolver {
 private static final ConcurrentHashMap<String,String>C=new ConcurrentHashMap<>();private ContactResolver(){}
 public static String name(Context c,String a){if(a==null||a.trim().isEmpty())return "Unknown";String n=C.get(a);if(n!=null)return n;if(c.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return a;Cursor x=null;try{Uri u=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(a));x=c.getContentResolver().query(u,new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},null,null,null);if(x!=null&&x.moveToFirst()){n=x.getString(0);if(n!=null&&!n.isEmpty()){C.put(a,n);return n;}}}catch(Exception ignored){}finally{if(x!=null)x.close();}return a;}
}
