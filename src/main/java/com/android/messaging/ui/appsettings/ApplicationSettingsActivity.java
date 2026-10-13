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
import android.widget.Switch;
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
        content.setBackgroundColor(AppPrefs.isDark(this) ? 0xff212121 : 0xfffafafa);
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
        colorRow("Accent color", AppPrefs.accent(this), "accent", true);
        colorRow("Outgoing message color", AppPrefs.outgoing(this), "outgoing", false);
        colorRow("Incoming message color", AppPrefs.incoming(this), "incoming", false);
        colorRow("Conversation background", AppPrefs.background(this), "background", false);
        row("UI scale (DPI)", AppPrefs.scale(this) + "%", this::chooseScale);

        section("Notifications");
        toggle("Notifications", "Show notifications for incoming SMS", AppPrefs.notifications(this), "notifications", false);
        toggle("Message preview", "Off by default; enabling exposes message text to Android notifications", AppPrefs.preview(this), "preview", false);
        toggle("Vibrate", "Vibrate for incoming messages", AppPrefs.vibrate(this), "vibrate", false);
        row("Default notification tone", toneLabel(AppPrefs.defaultTone(this)), this::pickTone);
        row("Rebuild notification channels", "Apply changed tones and vibration on Android 8+", () -> {
            NotificationHelper.clearChannels(this);
            Toast.makeText(this, "Notification channels will be recreated as messages arrive", Toast.LENGTH_SHORT).show();
        });

        section("Gestures");
        row("Swipe left", actionLabel(AppPrefs.swipeLeft(this)), () -> chooseAction("swipe_left"));
        row("Swipe right", actionLabel(AppPrefs.swipeRight(this)), () -> chooseAction("swipe_right"));

        section("Messaging");
        toggle("SMS delivery reports", "Ask the carrier for delivery status when supported", AppPrefs.deliveryReports(this), "delivery", false);
        toggle("Outgoing message sound", "Play a short local sound after sending", AppPrefs.sendSound(this), "send_sound", false);
        toggle("Confirm before deleting", "Confirm before permanently removing messages", AppPrefs.confirmDelete(this), "confirm_delete", false);

        section("Privacy");
        row("Storage & backup", "Encrypted private SMS, local backup and existing-history migration", () -> startActivity(new Intent(this, StorageSettingsActivity.class)));
        row("App permissions", "Manage local contacts, SMS and notifications", () -> com.android.messaging.sms.SmsRole.appSettings(this));
        toggle("Allow screenshots", "Off by default; also hides message screens from recent-app previews", AppPrefs.screenshots(this), "screenshots", true);
        toggle("Keyboard personalized learning", "Off by default for message and recipient fields", AppPrefs.keyboardLearning(this), "keyboard_learning", true);

        TextView note = new TextView(this);
        note.setText("No Internet permission. No analytics or accounts. New SMS and drafts use encrypted private storage. Existing shared history can be moved in Storage & backup.");
        note.setTextSize(13);
        note.setTextColor(AppPrefs.isDark(this) ? 0xffbdbdbd : 0xff757575);
        note.setPadding(UiUtil.dp(this, 18), UiUtil.dp(this, 24), UiUtil.dp(this, 18), UiUtil.dp(this, 36));
        content.addView(note);
    }

    private void section(String title) {
        TextView v = new TextView(this);
        v.setText(title.toUpperCase());
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
        if (AppPrefs.isDark(this)) { t.setTextColor(0xfff5f5f5); s.setTextColor(0xffbdbdbd); }
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
        TextView t = new TextView(this); t.setText(title); t.setTextSize(16); t.setTextColor(AppPrefs.isDark(this) ? 0xfff5f5f5 : 0xde000000); labels.addView(t);
        TextView s = new TextView(this); s.setText(summary); s.setTextSize(14); s.setTextColor(AppPrefs.isDark(this) ? 0xffbdbdbd : 0x8a000000); labels.addView(s);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this); sw.setChecked(value); sw.setOnCheckedChangeListener((button, checked) -> { AppPrefs.setBool(this, key, checked); if (recreate) recreate(); }); row.addView(sw);
        content.addView(row);
    }

    private String themeLabel() {
        String t = AppPrefs.theme(this);
        return "dark".equals(t) ? "Dark" : "light".equals(t) ? "Light" : "System";
    }

    private void chooseTheme() {
        String[] labels = {"System", "Light", "Dark"}; String[] values = {"system", "light", "dark"};
        new AlertDialog.Builder(this).setTitle("Theme").setItems(labels, (d, which) -> { AppPrefs.setString(this, "theme", values[which]); recreate(); }).show();
    }

    private void chooseScale() {
        final int[] values = {80, 90, 100, 110, 120, 130, 140, 150};
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = values[i] + "%";
        new AlertDialog.Builder(this).setTitle("UI scale (DPI)").setItems(labels, (d, which) -> {
            AppPrefs.setInt(this, "scale", values[which]); recreate();
        }).show();
    }

    private void colorRow(String title, int color, String key, boolean recreate) {
        row(title, UiUtil.hex(color), () -> chooseColor(title, color, key, recreate));
    }

    private void chooseColor(String title, int current, String key, boolean recreate) {
        String[] labels = {"AOSP blue", "Slate", "Indigo", "Teal", "Green", "Orange", "Red", "Purple", "Pink", "Dark grey", "White", "Custom hex…"};
        int[] colors = {0xff039be5,0xff607d8b,0xff3f51b5,0xff008577,0xff558b2f,0xffef6c00,0xffc62828,0xff7e57c2,0xffad1457,0xff424242,0xffffffff};
        new AlertDialog.Builder(this).setTitle(title).setItems(labels, (d, which) -> {
            if (which < colors.length) {
                AppPrefs.setInt(this, key, colors[which]);
                if (recreate) recreate(); else populate();
            } else customColor(title, current, key, recreate);
        }).show();
    }

    private void customColor(String title, int current, String key, boolean recreate) {
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_TEXT); input.setText(UiUtil.hex(current)); input.setSelection(input.length());
        new AlertDialog.Builder(this).setTitle(title + " — hex").setView(input)
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
        new AlertDialog.Builder(this).setTitle("Swipe action").setItems(labels, (d, which) -> { AppPrefs.setString(this, key, values[which]); populate(); }).show();
    }

    private void pickTone() {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, AppPrefs.defaultTone(this));
        startActivityForResult(i, PICK_TONE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_TONE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        AppPrefs.setString(this, "tone_default", uri == null ? AppPrefs.SILENT : uri.toString());
        NotificationHelper.clearChannels(this); populate();
    }

    private String toneLabel(Uri uri) {
        if (uri == null) return "Silent";
        try { Ringtone r = RingtoneManager.getRingtone(this, uri); return r == null ? uri.toString() : r.getTitle(this); }
        catch (Exception e) { return "System default"; }
    }
}
