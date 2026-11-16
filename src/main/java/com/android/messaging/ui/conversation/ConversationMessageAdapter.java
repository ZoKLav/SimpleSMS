package com.android.messaging.ui.conversation;
import android.view.LayoutInflater;import android.view.ViewGroup;import androidx.annotation.NonNull;import androidx.recyclerview.widget.RecyclerView;import com.android.messaging.R;import com.android.messaging.sms.SmsRepository;import com.android.messaging.util.DiffList;import java.util.*;
public final class ConversationMessageAdapter extends RecyclerView.Adapter<ConversationMessageAdapter.H> implements AutoCloseable {
 public interface Host{void menu(SmsRepository.Message value);void display(SmsRepository.Message value);}private Host host;private String address;private List<SmsRepository.Message> items=Collections.emptyList();
 private static final DiffList.Identity<SmsRepository.Message> IDENTITY=new DiffList.Identity<SmsRepository.Message>(){public boolean sameItem(SmsRepository.Message a,SmsRepository.Message b){return a.id==b.id;}public boolean sameContent(SmsRepository.Message a,SmsRepository.Message b){return com.android.messaging.util.MessageContent.same(a,b);}};
 private final DiffList<SmsRepository.Message> differ=new DiffList<>(IDENTITY,(values,diff)->{items=values;diff.dispatchUpdatesTo(this);});
 public ConversationMessageAdapter(String address,Host host){this.address=address;this.host=host;setHasStableIds(true);}
 public void setAddress(String address){this.address=address;}
 public void setItems(List<SmsRepository.Message> values,Object appearance,Runnable commit){differ.submit(values==null?Collections.emptyList():values,appearance,commit);}
 @NonNull public H onCreateViewHolder(@NonNull ViewGroup parent,int type){return new H((ConversationMessageView)LayoutInflater.from(parent.getContext()).inflate(R.layout.conversation_message_view,parent,false));}
 public void onBindViewHolder(@NonNull H holder,int position){SmsRepository.Message value=items.get(position);holder.view.bind(value,address);holder.view.actions(value.mms,v->{if(host!=null)host.display(value);},v->{if(host!=null)host.menu(value);return true;});}
 public int getItemCount(){return items.size();}public long getItemId(int position){return items.get(position).id;}
 public void close(){differ.close();host=null;items=Collections.emptyList();}
 static final class H extends RecyclerView.ViewHolder{final ConversationMessageView view;H(ConversationMessageView view){super(view);this.view=view;}}
}
