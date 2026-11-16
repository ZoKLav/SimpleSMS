package com.android.messaging.ui.appsettings;

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
import android.widget.ScrollView;
import androidx.appcompat.widget.SwitchCompat;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;

import com.android.messaging.R;
import com.android.messaging.util.AppPrefs;
import com.android.messaging.util.BaseActivity;
import com.android.messaging.util.NotificationHelper;
import com.android.messaging.util.UiUtil;

public final class ApplicationSettingsActivity extends BaseActivity {
    private static final int PICK_TONE = 3101;
    private LinearLayout content;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setTitle("Settings");
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setBackgroundDrawable(new ColorDrawable(AppPrefs.accent(this)));
        }
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(AppPrefs.isDark(this) ? AppPrefs.darkSurface(this,0) : 0xfff3f6f7);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        populate();
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void populate() {
        content.removeAllViews();
        section("Appearance");
        row("Theme", themeLabel(), () -> chooseTheme());
        row(getString(R.string.dark_shade_title),getString(R.string.dark_shade_value,AppPrefs.darkGrey(this)),this::chooseDarkGrey);
        colorRow("Accent color", AppPrefs.accent(this), "accent", true);
        colorRow("Outgoing message color", AppPrefs.outgoing(this), "outgoing", false);
        colorRow("Incoming message color", AppPrefs.incoming(this), "incoming", false);
        colorRow("Conversation background", AppPrefs.background(this), "background", false);
        row("UI scale (DPI)", AppPrefs.scale(this) + "%", this::chooseScale);

        section("Privacy & messaging");
        row("Notifications", "Android notification sound and lock-screen visibility", () -> startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        row("Default notification sound",toneLabel(AppPrefs.defaultTone(this)),this::pickTone);
        toggle("Vibrate","Vibrate for incoming messages",AppPrefs.vibrate(this),"vibrate",false);
        toggle("Show notification details", "Show sender and SMS text in Android notifications. With app lock on, previews require an unlocked app session.", AppPrefs.preview(this), "preview", false);
        toggle(getString(R.string.notification_actions_title),getString(R.string.notification_actions_summary), AppPrefs.notificationActions(this), "notification_actions", false);
        toggle(getString(R.string.notification_read_title),getString(R.string.notification_read_summary),AppPrefs.notificationMarkRead(this),"notification_mark_read",false);
        toggle("Hide MMS content", "Show Tap to display message until you open it. Also hides MMS text in lists and notifications.", AppPrefs.hideMms(this), "hide_mms", false);
        privateStorageToggle();
        row("Move existing SMS/MMS", "Encrypt existing shared messages, verify, then remove each original", () -> com.android.messaging.sms.ProviderMigration.confirm(this));
        boolean appLock=com.android.messaging.security.VaultKeys.lockEnabled(this);
        row("App lock", appLock ? "On — tap to turn off; private storage stays encrypted" : "Off — tap to require your device credential", () -> {
            new com.android.messaging.util.ScreenDialog.Builder(this).setTitle(appLock?"Turn off app lock?":"Enable app lock?")
                .setMessage(appLock?"Private data stays encrypted, but SimpleSMS will be able to use its key without asking for your credential. Your device or system app lock can control access. Existing encrypted data will be converted in place; keep the app open until finished.":"Require your device credential when opening SimpleSMS. A secure screen lock and hardware-enforced authentication are required. Existing private data will be converted in place; keep the app open until finished.")
                .setPositiveButton(appLock?"Turn off":"Enable",(d,w)->startActivity(new Intent(this,com.android.messaging.security.UnlockActivity.class).putExtra("lock_mode",appLock?"off":"on")))
                .setNegativeButton("Cancel",null).show();
        });
        if(appLock)row("Lock now", "Clear unlocked caches and return to device authentication", () -> {com.android.messaging.security.Vault.lock();NotificationHelper.redact(this);startActivity(new Intent(this,com.android.messaging.security.UnlockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();});

        section("Gestures");
        row("Swipe left", actionLabel(AppPrefs.swipeLeft(this)), () -> chooseAction("swipe_left"));
        row("Swipe right", actionLabel(AppPrefs.swipeRight(this)), () -> chooseAction("swipe_right"));

        section("Messaging");
        toggle("SMS delivery reports", "Ask the carrier for delivery status when supported", AppPrefs.deliveryReports(this), "delivery", false);
        toggle("Outgoing message sound", "Play a short local sound after sending", AppPrefs.sendSound(this), "send_sound", false);
        toggle("Confirm before deleting", "Confirm before permanently removing messages", AppPrefs.confirmDelete(this), "confirm_delete", false);

        section("Privacy");

        toggle("Keyboard personalized learning", "Off by default for message and recipient fields", AppPrefs.keyboardLearning(this), "keyboard_learning", true);

        TextView note = new TextView(this);
        note.setText(R.string.text_optional_private_sms_mms_storage_and_optional);
        note.setTextSize(13);
        note.setTextColor(AppPrefs.isDark(this) ? 0xffd5dce2 : 0xff757575);
        note.setPadding(UiUtil.dp(this, 18), UiUtil.dp(this, 24), UiUtil.dp(this, 18), UiUtil.dp(this, 36));
        content.addView(note);
    }

    private void privateStorageToggle(){
        boolean enabled=false;try{enabled=com.android.messaging.security.StorageMode.enabled(this);}catch(Exception ignored){}
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(UiUtil.dp(this,16),UiUtil.dp(this,12),UiUtil.dp(this,16),UiUtil.dp(this,16));
        SwitchCompat sw=new SwitchCompat(this);sw.setText(R.string.text_private_message_storage);sw.setTextSize(18);sw.setChecked(enabled);box.addView(sw);
        TextView warning=new TextView(this);warning.setText(R.string.text_warning_enabling_this_removes_new_incoming_and);warning.setTextSize(16);warning.setTypeface(null,android.graphics.Typeface.BOLD);warning.setTextColor(0xffb71c1c);warning.setBackgroundColor(0xffffebee);warning.setPadding(UiUtil.dp(this,12),UiUtil.dp(this,12),UiUtil.dp(this,12),UiUtil.dp(this,12));box.addView(warning);content.addView(box);
        sw.setOnCheckedChangeListener((button,checked)->{
            Runnable save=()->{try{com.android.messaging.security.StorageMode.set(this,checked);populate();}catch(Exception failure){Toast.makeText(this,failure.getMessage(),Toast.LENGTH_LONG).show();populate();}};
            if(checked){AlertDialog dialog=new com.android.messaging.util.ScreenDialog.Builder(this).setTitle("WARNING: Delete shared copies of new messages?")
                .setMessage("New incoming and outgoing SMS/MMS, including attachments, will be kept in SimpleSMS's private encrypted storage. Verified shared copies will be deleted from Android's message store. SimpleSMS will solely manage these messages. Other SMS apps will not see them, and clearing app data, uninstalling or losing the key can permanently lose them.\n\nThis does not move older history or encrypt carrier transport. Messages that cannot be verified or safely removed remain in shared storage.\n\nEnable only if you accept this change.")
                .setPositiveButton("Enable private storage",(d,w)->save.run()).setNegativeButton("Cancel",(d,w)->populate()).setOnCancelListener(d->populate()).create();
                dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xffb71c1c);});dialog.show();
            }else save.run();
        });
    }

    private void section(String title) {
        TextView v = new TextView(this);
        v.setText(title.toUpperCase(java.util.Locale.getDefault()));
        v.setTextColor(AppPrefs.accent(this));
        v.setTextSize(14);
        v.setPadding(UiUtil.dp(this, 16), UiUtil.dp(this, 24), UiUtil.dp(this, 16), UiUtil.dp(this, 8));
        content.addView(v);
    }

    private void row(String title, String summary, Runnable click) {
        View v = getLayoutInflater().inflate(R.layout.settings_item_view, content, false);
        TextView t = v.findViewById(R.id.title);
        TextView s = v.findViewById(R.id.summary);
        t.setText(title); s.setText(summary == null ? "" : summary);
        if (AppPrefs.isDark(this)) { t.setTextColor(0xffedf2f6); s.setTextColor(0xffd5dce2); }
        v.setOnClickListener(x -> click.run());
        content.addView(v);
    }

    private void toggle(String title, String summary, boolean value, String key, boolean recreate) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(UiUtil.dp(this, 64));
        row.setPadding(UiUtil.dp(this, 16), UiUtil.dp(this, 7), UiUtil.dp(this, 12), UiUtil.dp(this, 7));
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this); t.setText(title); t.setTextSize(16); t.setTextColor(AppPrefs.isDark(this) ? 0xffedf2f6 : 0xde000000); labels.addView(t);
        TextView s = new TextView(this); s.setText(summary); s.setTextSize(14); s.setTextColor(AppPrefs.isDark(this) ? 0xffd5dce2 : 0x8a000000); labels.addView(s);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        SwitchCompat sw = new SwitchCompat(this); sw.setChecked(value); sw.setOnCheckedChangeListener((button, checked) -> { AppPrefs.setBool(this, key, checked); if ("preview".equals(key) || "notification_actions".equals(key) || "notification_mark_read".equals(key) || "hide_mms".equals(key)) NotificationHelper.clearMessages(this); if (recreate) recreate(); }); row.addView(sw);
        content.addView(row);
    }

    private String themeLabel() {
        String t = AppPrefs.theme(this);
        return "dark".equals(t) ? "Dark" : "light".equals(t) ? "Light" : "System";
    }

    private void chooseDarkGrey(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);int pad=UiUtil.dp(this,20);box.setPadding(pad,pad,pad,pad);
        TextView preview=new TextView(this);preview.setTextColor(Color.WHITE);preview.setPadding(pad,pad,pad,pad);box.addView(preview);
        android.widget.SeekBar slider=new android.widget.SeekBar(this);slider.setMax(80);slider.setProgress(AppPrefs.darkGrey(this));box.addView(slider);
        Runnable refresh=()->{int value=slider.getProgress();preview.setText(getString(R.string.dark_shade_value,value));preview.setBackgroundColor(AppPrefs.greyColor(value,0));};refresh.run();
        slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(android.widget.SeekBar view,int value,boolean user){refresh.run();}public void onStartTrackingTouch(android.widget.SeekBar view){}public void onStopTrackingTouch(android.widget.SeekBar view){}});
        new com.android.messaging.util.ScreenDialog.Builder(this).setTitle(R.string.dark_shade_title).setView(box).setPositiveButton(android.R.string.ok,(dialog,which)->{AppPrefs.setInt(this,"dark_grey",slider.getProgress());recreate();}).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void chooseTheme() {
        String[] labels = {"System", "Light", "Dark"}; String[] values = {"system", "light", "dark"};
        new com.android.messaging.util.ScreenDialog.Builder(this).setTitle("Theme").setItems(labels, (d, which) -> { AppPrefs.setString(this, "theme", values[which]); recreate(); }).show();
    }

    private void chooseScale() {
        final int[] values = {80, 90, 100, 110, 120, 130, 140, 150};
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = values[i] + "%";
        new com.android.messaging.util.ScreenDialog.Builder(this).setTitle("UI scale (DPI)").setItems(labels, (d, which) -> {
            AppPrefs.setInt(this, "scale", values[which]); recreate();
        }).show();
    }

    private void colorRow(String title, int color, String key, boolean recreate) {
        row(title, UiUtil.hex(color), () -> chooseColor(title, color, key, recreate));
    }

    private void chooseColor(String title, int current, String key, boolean recreate) {
        String[] labels = {"AOSP blue", "Slate", "Indigo", "Teal", "Green", "Orange", "Red", "Purple", "Pink", "Dark grey", "White", "Custom hex…"};
        int[] colors = {0xff039be5,0xff607d8b,0xff3f51b5,0xff008577,0xff558b2f,0xffef6c00,0xffc62828,0xff7e57c2,0xffad1457,0xff424242,0xffffffff};
        new com.android.messaging.util.ScreenDialog.Builder(this).setTitle(title).setItems(labels, (d, which) -> {
            if (which < colors.length) {
                AppPrefs.setInt(this, key, colors[which]);
                if (recreate) recreate(); else populate();
            } else customColor(title, current, key, recreate);
        }).show();
    }

    private void customColor(String title, int current, String key, boolean recreate) {
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_TEXT); input.setText(UiUtil.hex(current)); input.setSelection(input.length());
        new com.android.messaging.util.ScreenDialog.Builder(this).setTitle(title + " — hex").setView(input)
                .setPositiveButton("Set", (d, w) -> {
                    try {
                        int color = Color.parseColor(input.getText().toString().trim());
                        AppPrefs.setInt(this, key, 0xff000000 | (color & 0x00ffffff));
                        if (recreate) recreate(); else populate();
                    } catch (Exception e) { Toast.makeText(this, "Use #RRGGBB", Toast.LENGTH_SHORT).show(); }
                }).setNegativeButton("Cancel", null).show();
    }

    private String actionLabel(String value) {
        if (AppPrefs.ARCHIVE.equals(value)) return "Archive / unarchive";
        if (AppPrefs.READ.equals(value)) return "Mark read";
        if (AppPrefs.DELETE.equals(value)) return "Delete";
        return "None";
    }

    private void chooseAction(String key) {
        String[] labels = {"None", "Archive / unarchive", "Mark read", "Delete"};
        String[] values = {AppPrefs.NONE, AppPrefs.ARCHIVE, AppPrefs.READ, AppPrefs.DELETE};
        new com.android.messaging.util.ScreenDialog.Builder(this).setTitle("Swipe action").setItems(labels, (d, which) -> { AppPrefs.setString(this, key, values[which]); populate(); }).show();
    }

    private void pickTone() {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, AppPrefs.defaultTone(this));
        try{startActivityForResult(i,PICK_TONE);}catch(android.content.ActivityNotFoundException unavailable){Toast.makeText(this,"No sound picker available",Toast.LENGTH_LONG).show();}
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_TONE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        AppPrefs.setString(this, "tone_default", uri == null ? AppPrefs.SILENT : uri.toString());
        populate();
    }

    private String toneLabel(Uri uri) {
        if (uri == null) return "Silent";
        try { Ringtone r = RingtoneManager.getRingtone(this, uri); return r == null ? uri.toString() : r.getTitle(this); }
        catch (Exception e) { return "System default"; }
    }
}
