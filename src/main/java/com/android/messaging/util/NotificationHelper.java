package com.android.messaging.util;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import com.android.messaging.R;
import com.android.messaging.security.UnlockActivity;
/** System notifications never contain addresses, sender names, text or message identifiers. */
public final class NotificationHelper {
 private static final String CHANNEL="simplesms_private_messages";private static final int ID=12001;
 private NotificationHelper(){}
 public static void incoming(Context c,String ignoredAddress,long ignoredThread,String ignoredBody){show(c,"New message");}
 public static void failure(Context c){show(c,"Message processing failed. Open SimpleSMS to check storage and device security.");}
 private static void show(Context c,String text){if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(n==null)return;if(Build.VERSION.SDK_INT>=26){NotificationChannel channel=new NotificationChannel(CHANNEL,"SimpleSMS messages",NotificationManager.IMPORTANCE_DEFAULT);channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);n.createNotificationChannel(channel);}PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,UnlockActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);n.notify(ID,b.setSmallIcon(R.drawable.ic_notification).setContentTitle("SimpleSMS").setContentText(text).setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_SECRET).build());}
 public static void cancel(Context c,long ignored){NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(n!=null)n.cancel(ID);}
 public static void clearChannels(Context c){if(Build.VERSION.SDK_INT<26)return;NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(n!=null)for(NotificationChannel channel:n.getNotificationChannels())if(channel.getId().startsWith("simplesms_")&&!CHANNEL.equals(channel.getId()))n.deleteNotificationChannel(channel.getId());}
}
