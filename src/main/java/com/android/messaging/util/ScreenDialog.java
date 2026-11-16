package com.android.messaging.util;
import android.app.*;import android.content.Context;import android.content.ContextWrapper;import android.os.Bundle;import java.lang.ref.WeakReference;import java.util.*;
public final class ScreenDialog {
 private static final List<Entry> OPEN=new ArrayList<>();private static WeakReference<Application> registered=new WeakReference<>(null);private ScreenDialog(){}
 private static final class Entry{final WeakReference<Activity> activity;final WeakReference<Dialog> dialog;Entry(Activity activity,Dialog dialog){this.activity=new WeakReference<>(activity);this.dialog=new WeakReference<>(dialog);}}
 private static Activity activity(Context context){while(context instanceof ContextWrapper){if(context instanceof Activity)return (Activity)context;Context next=((ContextWrapper)context).getBaseContext();if(next==context)break;context=next;}return null;}
 private static void register(Activity activity,Dialog dialog){
  if(activity==null)return;OPEN.removeIf(entry->entry.activity.get()==null||entry.dialog.get()==null);OPEN.add(new Entry(activity,dialog));if(registered.get()==activity.getApplication())return;registered=new WeakReference<>(activity.getApplication());
  activity.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
   public void onActivityDestroyed(Activity owner){Iterator<Entry> entries=OPEN.iterator();while(entries.hasNext()){Entry entry=entries.next();Activity screen=entry.activity.get();Dialog window=entry.dialog.get();if(screen==owner||screen==null||window==null){entries.remove();if(window!=null&&window.isShowing())window.dismiss();}}}
   public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}
  });
 }
 public static final class Builder extends AlertDialog.Builder{
  private final WeakReference<Activity> owner;public Builder(Context context){super(context);owner=new WeakReference<>(activity(context));}
  public AlertDialog create(){AlertDialog dialog=super.create();register(owner.get(),dialog);return dialog;}
 }
}
