package com.android.messaging.util;
import android.app.Activity;import android.os.Handler;import android.os.Looper;import java.lang.ref.WeakReference;import java.util.concurrent.Executor;import java.util.function.Consumer;
/** Tasks capture application context/data. Delivery uses its parameter, never captures the screen. */
public final class UiWork {
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 public interface Task<T>{@androidx.annotation.WorkerThread T run()throws Exception;}public interface Delivery<A extends Activity,T>{@androidx.annotation.MainThread void accept(A owner,T value,Exception error);}
 private UiWork(){}
 public static <A extends Activity,T>void run(A owner,Task<T> task,Delivery<A,T> delivery){run(owner,Workers.READ,task,delivery,value->{});}
 public static <A extends Activity,T>void run(A owner,Executor executor,Task<T> task,Delivery<A,T> delivery,Consumer<T> discard){
  WeakReference<A> reference=new WeakReference<>(owner);
  executor.execute(()->{T value=null;Exception error=null;try{value=task.run();}catch(Exception failure){error=failure;}T data=value;Exception failure=error;
   MAIN.post(()->{A activity=reference.get();if(activity==null||activity.isFinishing()||activity.isDestroyed()){if(data!=null)discard.accept(data);return;}delivery.accept(activity,data,failure);});
  });
 }
}
