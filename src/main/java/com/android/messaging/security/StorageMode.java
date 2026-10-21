package com.android.messaging.security;
import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
/** Non-secret routing policy, available to receivers while the app is locked. */
public final class StorageMode {
 private StorageMode(){}
 private static AtomicFile file(Context c){return new AtomicFile(new File(c.getNoBackupFilesDir(),"message-storage-mode"));}
 public static synchronized boolean enabled(Context c)throws IOException{AtomicFile f=file(c);if(!f.getBaseFile().exists()&&!new File(f.getBaseFile()+".bak").exists())return false;byte[] value=f.readFully();if(value.length!=1||(value[0]!=0&&value[0]!=1))throw new IOException("Message storage policy is damaged; open Settings to choose a storage mode");return value[0]==1;}
 public static synchronized void set(Context c,boolean enabled)throws Exception{Vault.require();if(enabled&&!com.android.messaging.sms.SmsRole.isDefault(c))throw new SecurityException("Set SimpleSMS as the default SMS app before enabling private storage");AtomicFile f=file(c);FileOutputStream out=null;try{out=f.startWrite();out.write(enabled?1:0);f.finishWrite(out);}catch(Exception e){if(out!=null)f.failWrite(out);throw e;}}
}
