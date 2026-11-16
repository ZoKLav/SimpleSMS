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
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .setData(Uri.parse("simplesms-action://message/"+id+"/"+operation))
            .putExtra("message_id",id).putExtra("operation",operation);
    }
    private static PendingIntent activity(Context c,long id,String op){
        return PendingIntent.getActivity(c,0,action(c,id,op),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static PendingIntent command(Context c,long id,String operation){
        boolean window=VaultKeys.lockEnabled(c)||("delete".equals(operation)&&AppPrefs.confirmDelete(c));
        if(window)return activity(c,id,operation);
        Intent intent=new Intent(c,NotificationCommandReceiver.class).setData(Uri.parse("simplesms-command://message/"+id+"/"+operation)).putExtra("message_id",id).putExtra("operation",operation);
        return PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
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
            Notification.Builder b=builder(c,address);if(b==null)return;
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
                boolean markRead=AppPrefs.notificationMarkRead(c);
                Notification.Action.Builder delete=new Notification.Action.Builder(markRead?android.R.drawable.ic_menu_view:android.R.drawable.ic_menu_delete,markRead?c.getString(R.string.mark_as_read):"Delete",command(c,id,markRead?"read":"delete"));
                if(Build.VERSION.SDK_INT>=31){reply.setAuthenticationRequired(true);delete.setAuthenticationRequired(true);}
                b.addAction(reply.build()).addAction(delete.build());
            }
            manager(c).notify(TAG+id,ID,b.build());
        }catch(Exception unavailable){failure(c);}
    }
    private static Notification.Builder builder(Context c){return builder(c,null);}
    private static String channel(Context c,String address){
        if(address==null||!Vault.unlocked())return CHANNEL;
        try{String profile=Vault.canonical(address)+"|"+String.valueOf(AppPrefs.tone(c,address))+"|"+AppPrefs.vibrate(c);
            byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(profile.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder key=new StringBuilder();for(byte value:digest)key.append(String.format(java.util.Locale.US,"%02x",value&255));return "simplesms_alert_"+Vault.thread("notification@"+key);
        }catch(Exception unavailable){return CHANNEL;}
    }
    private static Notification.Builder builder(Context c,String address){
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return null;
        NotificationManager n=manager(c);if(n==null)return null;String channelId=channel(c,address);Uri sound=address==null?AppPrefs.defaultTone(c):AppPrefs.tone(c,address);
        if(Build.VERSION.SDK_INT>=26&&n.getNotificationChannel(channelId)==null){NotificationChannel alerts=new NotificationChannel(channelId,CHANNEL.equals(channelId)?"SimpleSMS messages":"Conversation alerts",NotificationManager.IMPORTANCE_DEFAULT);alerts.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);alerts.setSound(sound,new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build());alerts.enableVibration(AppPrefs.vibrate(c));n.createNotificationChannel(alerts);}
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,channelId):new Notification.Builder(c);
        Notification.Builder hidden=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,channelId):new Notification.Builder(c);
        if(Build.VERSION.SDK_INT<26){b.setSound(sound);if(AppPrefs.vibrate(c))b.setVibrate(new long[]{0,200});}
        return b.setSmallIcon(R.drawable.ic_notification).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(hidden.setSmallIcon(R.drawable.ic_notification).setContentTitle("SimpleSMS").setContentText("New message").build());
    }
    public static void openSettings(android.app.Activity activity,String address){
        if(Build.VERSION.SDK_INT<26)return;builder(activity,address);
        try{activity.startActivity(new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,activity.getPackageName()).putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,channel(activity,address)));}
        catch(android.content.ActivityNotFoundException unavailable){android.widget.Toast.makeText(activity,"Notification settings unavailable",android.widget.Toast.LENGTH_LONG).show();}
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
    public static void dismissLoaded(Context c,java.util.List<com.android.messaging.sms.SmsRepository.Message> messages){
        NotificationManager n=manager(c);if(n==null)return;java.util.Map<Long,String> tags=new java.util.HashMap<>();
        for(android.service.notification.StatusBarNotification posted:n.getActiveNotifications()){String tag=posted.getTag();if(tag==null||!tag.startsWith(TAG))continue;try{tags.put(Long.parseLong(tag.substring(TAG.length())),tag);}catch(NumberFormatException ignored){}}
        for(com.android.messaging.sms.SmsRepository.Message message:messages){if(tags.isEmpty())break;String tag=tags.remove(message.id);if(tag!=null)n.cancel(tag,ID);}
    }
    public static void redact(Context c){
        NotificationManager n=manager(c);if(n==null)return;
        for(android.service.notification.StatusBarNotification posted:n.getActiveNotifications()){
            String tag=posted.getTag();if(tag==null||!tag.startsWith(TAG))continue;
            try{long id=Long.parseLong(tag.substring(TAG.length()));Notification.Builder b=builder(c);if(b!=null){if(Build.VERSION.SDK_INT>=26)b.setChannelId(posted.getNotification().getChannelId());n.notify(tag,ID,b.setContentTitle("SimpleSMS").setContentText("New message").setContentIntent(activity(c,id,"open")).setOnlyAlertOnce(true).setAutoCancel(true).build());}}catch(NumberFormatException ignored){}
        }
    }
    public static void clearMessages(Context c){NotificationManager n=manager(c);if(n!=null)n.cancelAll();}

}
