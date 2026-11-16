package com.android.messaging.util;
import android.os.Handler;import android.os.Looper;import com.android.messaging.security.Vault;import com.android.messaging.sms.ProviderMigration;import java.lang.ref.WeakReference;import java.util.concurrent.Executor;import java.util.function.BooleanSupplier;
/** Main-thread owner. Background jobs retain data and application context, not screens. */
public final class MessageLoader<T> implements AutoCloseable {
 public interface Read<T>{T run()throws Exception;}public interface Result<T>{void accept(T value,Exception error);}
 interface Dispatcher{void post(Runnable task);void delayed(Runnable task);void remove(Runnable task);}
 private static final class HandlerDispatcher implements Dispatcher{
  private final Handler handler=new Handler(Looper.getMainLooper());public void post(Runnable task){handler.post(task);}public void delayed(Runnable task){handler.postDelayed(task,150);}public void remove(Runnable task){handler.removeCallbacks(task);}
 }
 private final Dispatcher main;private final Executor worker;private final BooleanSupplier ready,moving;
 private Read<T> read;private Result<T> result;private boolean active,running,dirty,closed;private int session;private final Runnable start=this::start;
 public MessageLoader(Read<T> read,Result<T> result){this(read,result,Workers.READ,new HandlerDispatcher(),Vault::unlocked,ProviderMigration::isMoving);}
 MessageLoader(Read<T> read,Result<T> result,Executor worker,Dispatcher main,BooleanSupplier ready,BooleanSupplier moving){this.read=read;this.result=result;this.worker=worker;this.main=main;this.ready=ready;this.moving=moving;}
 public void invalidate(){session++;request();}public void resume(){if(closed)return;active=true;dirty=true;if(!running)start();}public void pause(){active=false;dirty=false;session++;main.remove(start);}
 public void request(){if(!active||closed)return;dirty=true;if(!running){main.remove(start);main.delayed(start);}}
 private void start(){
  if(!active||running||!dirty||closed)return;dirty=false;if(moving.getAsBoolean())return;running=true;
  int expected=session;Read<T> operation=read;BooleanSupplier canRead=ready;Dispatcher dispatch=main;WeakReference<MessageLoader<T>> owner=new WeakReference<>(this);
  worker.execute(()->{T value=null;Exception failure=null;try{if(canRead.getAsBoolean())value=operation.run();}catch(Exception error){failure=error;}T data=value;Exception error=failure;
   dispatch.post(()->{MessageLoader<T> loader=owner.get();if(loader==null||loader.closed)return;loader.running=false;
    if(loader.active&&loader.session==expected&&loader.ready.getAsBoolean()&&!loader.moving.getAsBoolean())loader.result.accept(data,error);
    if(loader.active&&loader.dirty)loader.main.delayed(loader.start);
   });
  });
 }
 public void close(){pause();closed=true;read=null;result=null;}
}
