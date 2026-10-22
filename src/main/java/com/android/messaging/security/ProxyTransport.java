package com.android.messaging.security;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.*;
import android.os.storage.StorageManager;
import android.system.*;
import com.android.messaging.sms.*;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
/** Seekable virtual file descriptors backed exclusively by RAM; required for native MMS readers. */
@TargetApi(26)
final class ProxyTransport {
 private static final HandlerThread THREAD=new HandlerThread("private-file-io");private static final Handler HANDLER;
 static{THREAD.start();HANDLER=new Handler(THREAD.getLooper());}
 private ProxyTransport(){}
 static ParcelFileDescriptor open(Context c,TransferProvider.Lease lease,boolean write)throws IOException{
  StorageManager storage=(StorageManager)c.getSystemService(Context.STORAGE_SERVICE);if(storage==null)throw new IOException("Private transport unavailable");
  return storage.openProxyFileDescriptor(write?ParcelFileDescriptor.MODE_WRITE_ONLY:ParcelFileDescriptor.MODE_READ_ONLY,new ProxyFileDescriptorCallback(){
   @Override public long onGetSize(){synchronized(lease){return lease.size;}}
   @Override public int onRead(long offset,int size,byte[] data)throws ErrnoException{synchronized(lease){if(write||lease.bytes==null||offset<0)throw new ErrnoException("read",OsConstants.EBADF);if(offset>=lease.size)return 0;int count=Math.min(size,lease.size-(int)offset);System.arraycopy(lease.bytes,(int)offset,data,0,count);return count;}}
   @Override public int onWrite(long offset,int size,byte[] data)throws ErrnoException{synchronized(lease){if(!write||offset<0||offset>lease.size||offset+size>10L*MmsTransport.MAX_BYTES)throw new ErrnoException("write",OsConstants.EFBIG);int end=(int)offset+size;if(lease.bytes==null)lease.bytes=new byte[Math.max(8192,end)];else if(end>lease.bytes.length)lease.bytes=Arrays.copyOf(lease.bytes,Math.max(end,Math.min(lease.bytes.length*2,10*MmsTransport.MAX_BYTES)));System.arraycopy(data,0,lease.bytes,(int)offset,size);lease.size=Math.max(lease.size,end);return size;}}
   @Override public void onFsync(){}
   @Override public void onRelease(){if(!write)return;new Thread(()->{byte[] bytes=null;try{synchronized(lease){if(lease.bytes==null||lease.size==0)throw new IOException("Empty MMS download");bytes=Arrays.copyOf(lease.bytes,lease.size);}long id=MmsStore.receive(c,bytes,lease.sub,lease.notification,lease.location);TransportStore.complete(c,lease.notification);lease.saved=true;try{MmsTransport.acknowledge(c,bytes,lease.location,lease.sub);}catch(Exception ignored){}}catch(Exception failure){com.android.messaging.util.NotificationHelper.failure(c);}finally{if(bytes!=null)Arrays.fill(bytes,(byte)0);lease.finished.countDown();}},"private-mms-seal").start();}
  },HANDLER);
 }
}
