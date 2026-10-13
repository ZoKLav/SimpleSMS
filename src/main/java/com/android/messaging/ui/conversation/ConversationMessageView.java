package com.android.messaging.ui.conversation;

import android.content.Context;
import android.graphics.Color;
import android.provider.Telephony;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.messaging.R;
import com.android.messaging.sms.SmsRepository.Message;
import com.android.messaging.ui.ContactIconView;
import com.android.messaging.util.AppPrefs;
import com.android.messaging.util.ContactResolver;
import com.android.messaging.util.MessageTimes;
import com.android.messaging.util.UiUtil;

public class ConversationMessageView extends FrameLayout {
    private LinearLayout row, bubble;
    private TextView text, status;
    private ContactIconView icon;
    public ConversationMessageView(Context c, AttributeSet a) { super(c, a); }
    @Override protected void onFinishInflate() {
        super.onFinishInflate(); row = findViewById(R.id.message_row); bubble = findViewById(R.id.message_content);
        text = findViewById(R.id.message_text); status = findViewById(R.id.message_status); icon = findViewById(R.id.conversation_icon);
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = View.MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        int maximum = Math.max(1, width - UiUtil.dp(getContext(), 88));
        text.setMaxWidth(maximum); status.setMaxWidth(maximum); super.onMeasure(widthSpec, heightSpec);
    }
    public void bind(Message m, String address) {
        boolean incoming = m.type == Telephony.Sms.MESSAGE_TYPE_INBOX;
        row.setGravity(incoming ? Gravity.START : Gravity.END);
        int color = incoming ? AppPrefs.incoming(getContext()) : AppPrefs.conversationColor(getContext(), address);
        bubble.setBackground(new MessageBubbleBackground(color, incoming, getResources().getDisplayMetrics().density));
        int foreground = UiUtil.readable(color);
        text.setTextColor(foreground); status.setTextColor(foreground == Color.WHITE ? 0xb3ffffff : 0x8a000000);
        text.setText(m.body);
        String label = m.dateSent > 0 ? "Sent" : (incoming ? "Received" : "Recorded");
        if (m.type == Telephony.Sms.MESSAGE_TYPE_FAILED) label = "Queued (failed)";
        else if (m.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || m.type == Telephony.Sms.MESSAGE_TYPE_QUEUED) label = "Queued (sending)";
        else if (!incoming && m.status == Telephony.Sms.STATUS_COMPLETE) label = "Sent (delivered)";
        status.setText(label + " · " + MessageTimes.exact(MessageTimes.displayed(m), DateFormat.is24HourFormat(getContext())));
        if (incoming) { icon.setVisibility(View.VISIBLE); icon.bind(ContactResolver.cachedName(getContext(), address), address); }
        else icon.setVisibility(View.GONE);
    }
}
