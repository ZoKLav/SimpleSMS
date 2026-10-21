package com.android.messaging.ui.conversationsettings;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import android.support.v7.app.ActionBar;

import com.android.messaging.R;
import com.android.messaging.util.AppPrefs;
import com.android.messaging.util.BaseActivity;
import com.android.messaging.util.ContactResolver;
import com.android.messaging.util.NotificationHelper;
import com.android.messaging.util.UiUtil;

public final class PeopleAndOptionsActivity extends BaseActivity {
    public static final String EXTRA_THREAD_ID = "thread_id";
    public static final String EXTRA_ADDRESS = "address";
    private static final int PICK_TONE = 3201;

    private long threadId = -1L;
    private String address;
    private LinearLayout content;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        threadId = getIntent().getLongExtra(EXTRA_THREAD_ID, -1L);
        address = getIntent().getStringExtra(EXTRA_ADDRESS);
        if(address==null&&com.android.messaging.security.Vault.unlocked())try{address=com.android.messaging.sms.SmsRepository.address(this,threadId);}catch(Exception ignored){}
        if (address == null || address.trim().isEmpty()) { finish(); return; }
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(AppPrefs.isDark(this) ? 0xff212121 : 0xfffafafa);
        setContentView(content);
        configureActionBar(); populate();
    }

    private void configureActionBar() {
        ActionBar bar = getSupportActionBar(); if (bar == null) return;
        int color = AppPrefs.conversationColor(this, address);
        bar.setBackgroundDrawable(new ColorDrawable(color));
        bar.setDisplayHomeAsUpEnabled(true);
        String name = ContactResolver.name(this, address); bar.setTitle(name); bar.setSubtitle(name.equals(address) ? null : address);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void populate() {
        content.removeAllViews();
        row("Conversation color", UiUtil.hex(AppPrefs.conversationColor(this, address)), this::chooseColor);
        if (threadId >= 0) row(AppPrefs.isArchived(this, threadId) ? "Unarchive conversation" : "Archive conversation",
                "Archived conversations stay out of the main list", () -> { AppPrefs.archive(this, threadId, !AppPrefs.isArchived(this, threadId)); populate(); });
        row("Reset conversation color", "Use the app-wide outgoing-message color", () -> { AppPrefs.clearConversationColor(this, address); configureActionBar(); populate(); });
    }

    private void row(String title, String summary, Runnable click) {
        View v = getLayoutInflater().inflate(R.layout.settings_item_view, content, false);
        TextView t = v.findViewById(R.id.title), s = v.findViewById(R.id.summary);
        t.setText(title); s.setText(summary);
        if (AppPrefs.isDark(this)) { t.setTextColor(0xfff5f5f5); s.setTextColor(0xffbdbdbd); }
        v.setOnClickListener(x -> click.run()); content.addView(v);
    }

    private void toggle(String title, String summary, boolean checked) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(UiUtil.dp(this,16),UiUtil.dp(this,8),UiUtil.dp(this,12),UiUtil.dp(this,8));
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this); t.setText(title); t.setTextSize(16); t.setTextColor(AppPrefs.isDark(this)?0xfff5f5f5:0xde000000); labels.addView(t);
        TextView s = new TextView(this); s.setText(summary); s.setTextSize(14); s.setTextColor(AppPrefs.isDark(this)?0xffbdbdbd:0x8a000000); labels.addView(s);
        row.addView(labels,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        Switch sw = new Switch(this); sw.setChecked(checked); sw.setOnCheckedChangeListener((b,v)->AppPrefs.setMuted(this,address,v)); row.addView(sw); content.addView(row);
    }

    private void chooseColor() {
        String[] labels = {"App default", "AOSP blue", "Slate", "Indigo", "Teal", "Green", "Orange", "Red", "Purple", "Pink", "Custom hex…"};
        int[] colors = {0xff039be5,0xff607d8b,0xff3f51b5,0xff008577,0xff558b2f,0xffef6c00,0xffc62828,0xff7e57c2,0xffad1457};
        new AlertDialog.Builder(this).setTitle("Conversation color").setItems(labels, (d, which) -> {
            if (which == 0) { AppPrefs.clearConversationColor(this,address); recreate(); }
            else if (which <= colors.length) { AppPrefs.setConversationColor(this,address,colors[which-1]); recreate(); }
            else customColor();
        }).show();
    }

    private void customColor() {
        EditText input = new EditText(this); input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_TEXT); input.setText(UiUtil.hex(AppPrefs.conversationColor(this,address))); input.setSelection(input.length());
        new AlertDialog.Builder(this).setTitle("Conversation color — hex").setView(input)
                .setPositiveButton("Set",(d,w)->{try{int c=Color.parseColor(input.getText().toString().trim());AppPrefs.setConversationColor(this,address,0xff000000|(c&0x00ffffff));recreate();}catch(Exception e){Toast.makeText(this,"Use #RRGGBB",Toast.LENGTH_SHORT).show();}})
                .setNegativeButton("Cancel",null).show();
    }

    private void pickTone() {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_NOTIFICATION);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,true);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,AppPrefs.tone(this,address));
        startActivityForResult(i,PICK_TONE);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_TONE||resultCode!=RESULT_OK||data==null)return;
        Uri uri=data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        AppPrefs.setContactTone(this,address,uri==null?AppPrefs.SILENT:uri.toString()); NotificationHelper.clearChannels(this); populate();
    }

    private String toneLabel(Uri uri) {
        if(uri==null)return "Silent";
        try{Ringtone r=RingtoneManager.getRingtone(this,uri);return r==null?uri.toString():r.getTitle(this);}catch(Exception e){return "System default";}
    }
}
