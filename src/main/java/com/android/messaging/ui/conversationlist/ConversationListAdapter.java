package com.android.messaging.ui.conversationlist;
import android.view.LayoutInflater;import android.view.ViewGroup;import androidx.annotation.NonNull;import androidx.recyclerview.widget.RecyclerView;import com.android.messaging.R;import com.android.messaging.sms.SmsRepository;import com.android.messaging.util.DiffList;import java.util.*;
public final class ConversationListAdapter extends RecyclerView.Adapter<ConversationListAdapter.H> implements AutoCloseable {
 public interface Host{void open(SmsRepository.Conversation value);void menu(SmsRepository.Conversation value);default void itemsChanged(){}}private Host host;private List<SmsRepository.Conversation> all=Collections.emptyList();private String filter="";private Object appearance;private List<SmsRepository.Conversation> items=Collections.emptyList();
 private static final DiffList.Identity<SmsRepository.Conversation> IDENTITY=new DiffList.Identity<SmsRepository.Conversation>(){public boolean sameItem(SmsRepository.Conversation a,SmsRepository.Conversation b){return a.threadId==b.threadId;}public boolean sameContent(SmsRepository.Conversation a,SmsRepository.Conversation b){return a.date==b.date&&a.type==b.type&&a.unread==b.unread&&Objects.equals(a.title,b.title)&&Objects.equals(a.address,b.address)&&Objects.equals(a.snippet,b.snippet);}};
 private final DiffList<SmsRepository.Conversation> differ=new DiffList<>(IDENTITY,(values,diff)->{items=values;diff.dispatchUpdatesTo(this);if(host!=null)host.itemsChanged();});
 public ConversationListAdapter(Host host){this.host=host;setHasStableIds(true);}
 public void setItems(List<SmsRepository.Conversation> values,Object style){appearance=style;all=values==null?Collections.emptyList():new ArrayList<>(values);apply();}
 public void filter(String value){filter=value==null?"":value.trim().toLowerCase(Locale.ROOT);apply();}
 private boolean matches(String value){return value!=null&&value.toLowerCase(Locale.ROOT).contains(filter);}
 private void apply(){List<SmsRepository.Conversation> next=new ArrayList<>();for(SmsRepository.Conversation value:all)if(filter.isEmpty()||matches(value.title)||matches(value.address)||matches(value.snippet))next.add(value);differ.submit(next,appearance,null);}
 public SmsRepository.Conversation get(int position){return items.get(position);}
 @NonNull public H onCreateViewHolder(@NonNull ViewGroup parent,int type){return new H((ConversationListItemView)LayoutInflater.from(parent.getContext()).inflate(R.layout.conversation_list_item_view,parent,false));}
 public void onBindViewHolder(@NonNull H holder,int position){SmsRepository.Conversation value=items.get(position);holder.view.bind(value);holder.view.setOnClickListener(v->{if(host!=null)host.open(value);});holder.view.setOnLongClickListener(v->{if(host!=null)host.menu(value);return true;});}
 public int getItemCount(){return items.size();}public long getItemId(int position){return items.get(position).threadId;}
 public void close(){differ.close();host=null;items=Collections.emptyList();all=Collections.emptyList();}
 static final class H extends RecyclerView.ViewHolder{final ConversationListItemView view;H(ConversationListItemView view){super(view);this.view=view;}}
}
