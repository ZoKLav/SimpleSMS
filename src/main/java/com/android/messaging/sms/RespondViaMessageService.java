package com.android.messaging.sms;
import android.app.Service;import android.content.*;import android.net.Uri;import android.os.*;import com.android.messaging.util.Workers;import java.lang.ref.WeakReference;
public final class RespondViaMessageService extends Service{
 private boolean destroyed;
 public int onStartCommand(Intent intent,int flags,int startId){
  if(intent==null||!"android.intent.action.RESPOND_VIA_MESSAGE".equals(intent.getAction())){stopSelf(startId);return START_NOT_STICKY;}
  Uri uri=intent.getData();String raw=uri==null?null:uri.getSchemeSpecificPart();if(raw!=null){int query=raw.indexOf('?');if(query>=0)raw=raw.substring(0,query);}
  final String address=raw,body=intent.getStringExtra(Intent.EXTRA_TEXT);final Context app=getApplicationContext();final WeakReference<RespondViaMessageService> owner=new WeakReference<>(this);final Handler main=new Handler(Looper.getMainLooper());
  Workers.INBOUND.execute(()->{try{if(address!=null&&body!=null&&!body.trim().isEmpty())SmsSender.send(app,address,body);}catch(Exception ignored){}main.post(()->{RespondViaMessageService service=owner.get();if(service!=null&&!service.destroyed)service.stopSelf(startId);});});return START_NOT_STICKY;
 }
 public void onDestroy(){destroyed=true;super.onDestroy();}public IBinder onBind(Intent intent){return null;}
}
