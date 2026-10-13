package com.android.messaging.ui.conversation;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.android.messaging.R;
import com.android.messaging.sms.SmsRepository.Message;
import java.util.ArrayList;
import java.util.List;

public final class ConversationMessageAdapter extends RecyclerView.Adapter<ConversationMessageAdapter.Holder> {
    public interface Host { void menu(Message m); void click(Message m); boolean selected(long id); }
    private final String address;
    private final Host host;
    private final ArrayList<Message> items = new ArrayList<>();
    public ConversationMessageAdapter(String address, Host host) { this.address = address; this.host = host; setHasStableIds(true); }
    public void setItems(List<Message> data) { items.clear(); if (data != null) items.addAll(data); notifyDataSetChanged(); }
    @Override public long getItemId(int position) { return items.get(position).id; }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder((ConversationMessageView) LayoutInflater.from(parent.getContext()).inflate(R.layout.conversation_message_view, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        Message m = items.get(position); h.view.bind(m, address == null ? m.address : address);
        h.view.setBackgroundColor(host.selected(m.id) ? 0x33448aff : android.graphics.Color.TRANSPARENT);
        h.view.setOnClickListener(v -> host.click(m)); h.view.setOnLongClickListener(v -> { host.menu(m); return true; });
    }
    @Override public int getItemCount() { return items.size(); }
    static final class Holder extends RecyclerView.ViewHolder {
        final ConversationMessageView view; Holder(ConversationMessageView v) { super(v); view = v; }
    }
}
