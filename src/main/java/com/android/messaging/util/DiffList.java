package com.android.messaging.util;
import android.os.Handler;import android.os.Looper;import androidx.recyclerview.widget.DiffUtil;import java.lang.ref.WeakReference;import java.util.*;import java.util.concurrent.Executor;import java.util.function.Consumer;
public final class DiffList<T> implements AutoCloseable {
 public interface Identity<T>{boolean sameItem(T before,T after);boolean sameContent(T before,T after);}public interface Observer<T>{void changed(List<T> items,DiffUtil.DiffResult diff);}
 private final Identity<T> identity;private final Executor worker;private final Consumer<Runnable> main;private Observer<T> observer;private List<T> current=Collections.emptyList();private Object appearance;private Runnable commit;private int generation;private boolean closed;
 public DiffList(Identity<T> identity,Observer<T> observer){this(identity,observer,Workers.DIFF,new Handler(Looper.getMainLooper())::post);}
 DiffList(Identity<T> identity,Observer<T> observer,Executor worker,Consumer<Runnable> main){this.identity=identity;this.observer=observer;this.worker=worker;this.main=main;}
 public void submit(List<T> values,Object style,Runnable completion){
  if(closed)return;List<T> before=current,after=Collections.unmodifiableList(new ArrayList<>(values));boolean restyle=!Objects.equals(appearance,style);int expected=++generation;commit=completion;
  Identity<T> compare=identity;Consumer<Runnable> delivery=main;WeakReference<DiffList<T>> owner=new WeakReference<>(this);
  worker.execute(()->{DiffUtil.DiffResult diff=DiffUtil.calculateDiff(new SnapshotDiff<>(before,after,compare,restyle),false);
   delivery.accept(()->{DiffList<T> list=owner.get();if(list==null||list.closed||list.generation!=expected)return;list.current=after;list.appearance=style;Runnable done=list.commit;list.commit=null;list.observer.changed(after,diff);if(done!=null&&!list.closed&&list.generation==expected)done.run();});
  });
 }
 private static final class SnapshotDiff<T> extends DiffUtil.Callback{
  private final List<T> before,after;private final Identity<T> identity;private final boolean restyle;
  SnapshotDiff(List<T> before,List<T> after,Identity<T> identity,boolean restyle){this.before=before;this.after=after;this.identity=identity;this.restyle=restyle;}
  public int getOldListSize(){return before.size();}public int getNewListSize(){return after.size();}public boolean areItemsTheSame(int a,int b){return identity.sameItem(before.get(a),after.get(b));}public boolean areContentsTheSame(int a,int b){return !restyle&&identity.sameContent(before.get(a),after.get(b));}
 }
 public void close(){closed=true;generation++;observer=null;commit=null;current=Collections.emptyList();appearance=null;}
}
