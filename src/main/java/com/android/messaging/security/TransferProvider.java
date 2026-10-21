package com.android.messaging.security;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import com.android.messaging.sms.*;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
/** Short-lived URI grants backed by RAM pipes. No plaintext transport/attachment files. */
public final class TransferProvider extends ContentProvider {
 private static final long LIFETIME=120000;
 private static final Map<String,Lease> leases=new ConcurrentHashMap<>();
 public static final class Lease {public byte[] bytes;public String mime,location;public int sub;public long notification;public boolean download;public int size;long expires;public final CountDownLatch finished=new CountDownLatch(1);public volatile boolean saved;}
 @Override public boolean onCreate(){return true;}
 private static Uri register(Context c,Lease lease){String token=UUID.randomUUID().toString();lease.expires=SystemClock.elapsedRealtime()+LIFETIME;leases.put(token,lease);new Handler(Looper.getMainLooper()).postDelayed(()->release(Uri.parse("content://"+c.getPackageName()+".private/transfer/"+token)),LIFETIME);return Uri.parse("content://"+c.getPackageName()+".private/transfer/"+token);}
 public static Uri outgoing(Context c,byte[] bytes,String mime){Lease lease=new Lease();lease.bytes=bytes;lease.size=bytes.length;lease.mime=mime;return register(c,lease);}
 public static Uri incoming(Context c,long notification,String location,int sub){Lease lease=new Lease();lease.download=true;lease.notification=notification;lease.location=location;lease.sub=sub;lease.mime="application/vnd.wap.mms-message";return register(c,lease);}
 public static Lease find(Uri uri){Lease lease=leases.get(uri.getLastPathSegment());return lease!=null&&lease.expires>SystemClock.elapsedRealtime()?lease:null;}
 public static void release(Uri uri){Lease lease=leases.remove(uri.getLastPathSegment());if(lease!=null&&lease.bytes!=null){Arrays.fill(lease.bytes,(byte)0);lease.bytes=null;}}
 @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
  Lease lease=find(uri);if(lease==null)throw new FileNotFoundException("Transfer expired or process restarted");boolean write="w".equals(mode)||"wt".equals(mode)||"rw".equals(mode)||"rwt".equals(mode);if(write!=lease.download)throw new FileNotFoundException("Wrong transfer mode");
  if(android.os.Build.VERSION.SDK_INT>=26)try{return ProxyTransport.open(getContext(),lease,write);}catch(java.io.IOException e){throw new FileNotFoundException(e.getMessage());}
  if(write)throw new FileNotFoundException("Private MMS transport requires Android 8 or later");
  try{ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createReliablePipe();   byte[] bytes=lease.bytes;if(bytes==null)throw new FileNotFoundException("Transfer unavailable");new Thread(()->{try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){out.write(bytes);}catch(IOException ignored){}},"private-read-pipe").start();return pipe[0];
  }catch(IOException e){throw new FileNotFoundException(e.getMessage());}
 }
 @Override public String getType(Uri uri){Lease lease=find(uri);return lease==null?"application/octet-stream":lease.mime;}
 @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){Lease lease=find(uri);if(lease==null)return null;MatrixCursor cursor=new MatrixCursor(new String[]{"_display_name","_size"});cursor.addRow(new Object[]{"attachment",lease.bytes==null?null:lease.bytes.length});return cursor;}
 @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}@Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}@Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
}
