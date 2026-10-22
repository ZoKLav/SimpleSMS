package com.android.messaging.util;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import com.android.messaging.R;
import com.android.messaging.security.*;

/** Content previews are opt-in. Intents carry only an opaque message identifier. */
public final class NotificationHelper {
    private static final String CHANNEL="simplesms_messages_v2", TAG="message:";
    private static final int ID=12001;
    private NotificationHelper(){}
    private static NotificationManager manager(Context c){return (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);}
    public static Intent action(Context c,long id,String operation){
        return new Intent(c,NotificationActionActivity.class)
            .setData(Uri.parse("simplesms-action://message/"+id+"/"+operation))
            .putExtra("message_id",id).putExtra("operation",operation);
    }
    private static PendingIntent activity(Context c,long id,String op){
        return PendingIntent.getActivity(c,0,action(c,id,op),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void incoming(Context c,long id,String address,String body,boolean mms){
        try {
            // No credential prompt in a receiver. Locked sessions use generic notifications.
            if(!Vault.unlocked()&&!VaultKeys.lockEnabled(c))Vault.unlock(c);
            if(!AppPrefs.notifications(c)||AppPrefs.muted(c,address))return;
            KeyguardManager km=(KeyguardManager)c.getSystemService(Context.KEYGUARD_SERVICE);
            boolean accessible=Vault.unlocked()&&!(VaultKeys.lockEnabled(c)&&km!=null&&km.isDeviceLocked());
            boolean preview=accessible&&AppPrefs.preview(c);
            String title=preview?ContactResolver.name(c,address):"SimpleSMS";
            String text=preview?((mms&&AppPrefs.hideMms(c))?"MMS · Tap to display message":body):"New message";
            if(title!=null&&title.length()>200)title=title.substring(0,200);
            if(text!=null&&text.length()>4000)text=text.substring(0,4000)+"…";
            Notification.Builder b=builder(c);if(b==null)return;
            b.setContentTitle(title).setContentText(text).setContentIntent(activity(c,id,"open"))
                .setStyle(new Notification.BigTextStyle().bigText(text)).setAutoCancel(true);
            if(accessible&&AppPrefs.notificationActions(c)){
                Notification.Action.Builder reply;
                if(!VaultKeys.lockEnabled(c)){
                    Intent i=new Intent(c,NotificationReplyReceiver.class)
                        .setData(Uri.parse("simplesms-reply://message/"+id)).putExtra("message_id",id);
                    int flags=PendingIntent.FLAG_UPDATE_CURRENT;
                    if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
                    PendingIntent pi=PendingIntent.getBroadcast(c,0,i,flags);
                    reply=new Notification.Action.Builder(android.R.drawable.ic_menu_send,"Reply",pi)
                        .addRemoteInput(new RemoteInput.Builder("reply").setLabel("Reply").build());
                }else reply=new Notification.Action.Builder(android.R.drawable.ic_menu_send,"Reply",activity(c,id,"reply"));
                Notification.Action.Builder delete=new Notification.Action.Builder(android.R.drawable.ic_menu_delete,"Delete",activity(c,id,"delete"));
                if(Build.VERSION.SDK_INT>=31){reply.setAuthenticationRequired(true);delete.setAuthenticationRequired(true);}
                b.addAction(reply.build()).addAction(delete.build());
            }
            manager(c).notify(TAG+id,ID,b.build());
        }catch(Exception unavailable){failure(c);}
    }
    private static Notification.Builder builder(Context c){
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return null;
        NotificationManager n=manager(c);if(n==null)return null;
        if(Build.VERSION.SDK_INT>=26){NotificationChannel channel=new NotificationChannel(CHANNEL,"SimpleSMS messages",NotificationManager.IMPORTANCE_DEFAULT);channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);n.createNotificationChannel(channel);}
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        Notification.Builder hidden=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        return b.setSmallIcon(R.drawable.ic_notification).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(hidden.setSmallIcon(R.drawable.ic_notification).setContentTitle("SimpleSMS").setContentText("New message").build());
    }
    public static void failure(Context c){
        Notification.Builder b=builder(c);if(b==null)return;
        PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,UnlockActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        manager(c).notify(ID,b.setContentTitle("SimpleSMS").setContentText("Message processing failed. Open SimpleSMS to check.").setContentIntent(open).setAutoCancel(true).build());
    }
    public static void replyFailed(Context c,long id,boolean saved){
        Notification.Builder b=builder(c);if(b!=null)manager(c).notify(TAG+id,ID,b.setContentTitle("SimpleSMS").setContentText(saved?"Reply not sent. Tap to open the saved draft.":"Reply could not be saved or sent. Open SimpleSMS to check storage.").setContentIntent(activity(c,id,"reply")).setAutoCancel(true).build());
    }
    public static void dismiss(Context c,long id){NotificationManager n=manager(c);if(n!=null)n.cancel(TAG+id,ID);}
    public static void cancel(Context c,long thread){
        new Thread(()->{try{for(com.android.messaging.sms.SmsRepository.Message m:com.android.messaging.sms.SmsRepository.messages(c,thread))dismiss(c,m.id);}catch(Exception ignored){}},"notification-clear").start();
    }
    public static void redact(Context c){
        NotificationManager n=manager(c);if(n==null)return;
        for(android.service.notification.StatusBarNotification posted:n.getActiveNotifications()){
            String tag=posted.getTag();if(tag==null||!tag.startsWith(TAG))continue;
            try{long id=Long.parseLong(tag.substring(TAG.length()));Notification.Builder b=builder(c);if(b!=null)n.notify(tag,ID,b.setContentTitle("SimpleSMS").setContentText("New message").setContentIntent(activity(c,id,"open")).setOnlyAlertOnce(true).setAutoCancel(true).build());}catch(NumberFormatException ignored){}
        }
    }
    public static void clearMessages(Context c){NotificationManager n=manager(c);if(n!=null)n.cancelAll();}
    public static void clearChannels(Context c){if(Build.VERSION.SDK_INT<26)return;NotificationManager n=manager(c);if(n!=null)for(NotificationChannel channel:n.getNotificationChannels())if(channel.getId().startsWith("simplesms_")&&!CHANNEL.equals(channel.getId()))n.deleteNotificationChannel(channel.getId());}
}
