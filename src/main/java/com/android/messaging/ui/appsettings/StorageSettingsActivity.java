package com.android.messaging.ui.appsettings;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.android.messaging.storage.LocalBackup;
import com.android.messaging.util.AppPrefs;
import com.android.messaging.util.BaseActivity;
import com.android.messaging.util.UiUtil;
import com.android.messaging.util.Workers;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public final class StorageSettingsActivity extends BaseActivity {
    private static final int EXPORT = 4101, RESTORE = 4102;
    private LinearLayout content;
    private ProgressDialog progress;
    private boolean busy;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getSupportActionBar() != null) { getSupportActionBar().setTitle("Storage & backup"); getSupportActionBar().setDisplayHomeAsUpEnabled(true); }
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtil.dp(this, 20); content.setPadding(pad, pad, pad, pad);
        scroll.addView(content); setContentView(scroll);
        note("New SMS messages and drafts are encrypted in SimpleSMS's private storage. Existing messages in Android's shared SMS store keep their original protection until you move them here.");
        button("Copy existing SMS into encrypted storage", () -> confirmImport(false));
        button("Move existing SMS into encrypted storage", () -> confirmImport(true));
        note("An encrypted backup contains the private message history, including dates and send status. Drafts, contacts and appearance settings are excluded. Keep the password: it cannot be reset.");
        button("Create encrypted backup", () -> picker(true));
        button("Restore encrypted backup", () -> picker(false));
        note("Choose files on this device. Automatic cloud backup is disabled. SMS still uses your mobile carrier; local storage encryption does not encrypt the SMS network.");
    }
    private void note(String text) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(15);
        v.setTextColor(AppPrefs.isDark(this) ? 0xffeeeeee : 0xff333333);
        v.setPadding(0, UiUtil.dp(this, 12), 0, UiUtil.dp(this, 16)); content.addView(v);
    }
    private void button(String label, Runnable click) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setOnClickListener(v -> { if (!busy) click.run(); }); content.addView(b);
    }
    private void confirmImport(boolean move) {
        new AlertDialog.Builder(this).setTitle(move ? "Move existing messages?" : "Copy existing messages?")
                .setMessage(move ? "Each encrypted copy is verified before its shared original is deleted. Moved messages will be visible only in SimpleSMS. This does not securely erase old flash pages."
                        : "Create encrypted private copies. Originals remain in Android's shared SMS store and can still be read by other permitted apps.")
                .setPositiveButton(move ? "Move" : "Copy", (d, w) -> run(() -> {
                    int n = LocalBackup.importSystem(this, move); return n + " messages " + (move ? "moved" : "copied");
                })).setNegativeButton("Cancel", null).show();
    }
    private void picker(boolean export) {
        Intent i = new Intent(export ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType(export ? "application/octet-stream" : "*/*");
        i.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        if (export) i.putExtra(Intent.EXTRA_TITLE, "SimpleSMS-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()) + ".ssms");
        try { startActivityForResult(i, export ? EXPORT : RESTORE); }
        catch (android.content.ActivityNotFoundException e) { result("No local file picker is available"); }
    }
    @Override protected void onActivityResult(int request, int code, Intent data) {
        super.onActivityResult(request, code, data);
        if ((request != EXPORT && request != RESTORE) || code != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        try { LocalBackup.requireLocal(uri); } catch (Exception e) { result(e.getMessage()); return; }
        password(uri, request == EXPORT);
    }
    private EditText passwordField(String hint) {
        EditText field = new EditText(this); field.setHint(hint); field.setSaveEnabled(false);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setImeOptions(field.getImeOptions() | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        if (android.os.Build.VERSION.SDK_INT >= 26) field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        return field;
    }
    private void password(Uri uri, boolean export) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtil.dp(this, 20); box.setPadding(pad, 0, pad, 0);
        EditText first = passwordField("Password (at least 12 characters)"); box.addView(first);
        EditText repeat = export ? passwordField("Repeat password") : null;
        if (repeat != null) box.addView(repeat);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(export ? "Backup password" : "Restore password")
                .setView(box).setPositiveButton(export ? "Create" : "Restore", null).setNegativeButton("Cancel", null).create();
        dialog.setOnDismissListener(d -> { first.setText(""); if (repeat != null) repeat.setText(""); });
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            char[] secret = new char[first.length()]; first.getText().getChars(0, first.length(), secret, 0);
            if (secret.length < 12) { first.setError("Use at least 12 characters"); Arrays.fill(secret, '\0'); return; }
            if (repeat != null) {
                char[] check = new char[repeat.length()]; repeat.getText().getChars(0, repeat.length(), check, 0);
                boolean match = Arrays.equals(secret, check); Arrays.fill(check, '\0');
                if (!match) { repeat.setError("Passwords do not match"); Arrays.fill(secret, '\0'); return; }
            }
            dialog.dismiss();
            run(() -> {
                try {
                    int n = export ? LocalBackup.export(this, uri, secret) : LocalBackup.restore(this, uri, secret);
                    return export ? n + " private messages backed up" : n + " messages added; restored messages are never sent automatically";
                } finally { Arrays.fill(secret, '\0'); }
            });
        }));
        dialog.show();
    }
    private interface Operation { String perform() throws Exception; }
    private void run(Operation operation) {
        if (busy) return; busy = true;
        progress = ProgressDialog.show(this, "", "Working…", true, false);
        Workers.IO.execute(() -> {
            String text;
            try { text = operation.perform(); }
            catch (Exception e) { text = "Operation could not be completed. Check the password, SMS role, permissions and free storage. Existing messages were kept when verification failed."; }
            final String message = text;
            runOnUiThread(() -> {
                busy = false;
                if (progress != null) { progress.dismiss(); progress = null; }
                if (!isDestroyed() && !isFinishing()) result(message);
            });
        });
    }
    private void result(String message) { new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show(); }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; } return super.onOptionsItemSelected(item);
    }
    @Override protected void onDestroy() { if (progress != null) { progress.dismiss(); progress = null; } super.onDestroy(); }
}
