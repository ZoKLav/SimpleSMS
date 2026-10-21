package com.android.messaging.security;
import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;
/** Clear unlocked caches after backgrounding; no plaintext session is persisted. */
public final class PrivateApplication extends Application implements Application.ActivityLifecycleCallbacks {
 private final Handler handler=new Handler(Looper.getMainLooper());private final Set<Activity> screens=new HashSet<>();private int started;
 private final Runnable lock=()->{if(!VaultKeys.lockEnabled(this))return;Vault.lock();for(Activity a:new HashSet<>(screens))if(!(a instanceof UnlockActivity))a.finish();};
 public void expireSession(){if(!VaultKeys.lockEnabled(this))return;boolean foreground=started>0;lock.run();if(foreground)startActivity(new Intent(this,UnlockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));}
 @Override public void onCreate(){super.onCreate();registerActivityLifecycleCallbacks(this);BroadcastReceiver off=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){handler.removeCallbacks(lock);lock.run();}};IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_OFF);if(Build.VERSION.SDK_INT>=33)registerReceiver(off,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(off,f);}
 public void onActivityCreated(Activity a,Bundle b){screens.add(a);}public void onActivityStarted(Activity a){started++;handler.removeCallbacks(lock);}public void onActivityStopped(Activity a){started=Math.max(0,started-1);if(started==0)handler.postDelayed(lock,30000);}public void onActivityDestroyed(Activity a){screens.remove(a);}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}
}
