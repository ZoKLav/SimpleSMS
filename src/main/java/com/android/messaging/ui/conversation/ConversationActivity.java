package com.android.messaging.ui.conversation;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.android.messaging.R;
import com.android.messaging.sms.SmsRepository;
import com.android.messaging.sms.SmsRole;
import com.android.messaging.sms.SmsSender;
import com.android.messaging.ui.conversationsettings.PeopleAndOptionsActivity;
import com.android.messaging.util.AppPrefs;
import com.android.messaging.util.BaseActivity;
import com.android.messaging.util.ContactResolver;
import com.android.messaging.util.NotificationHelper;

import java.util.List;

/**
 * SMS-only conversation screen. This intentionally keeps the AOSP Messaging
 * conversation/activity/composer structure while removing media and MMS compose paths.
 */
public final class ConversationActivity extends BaseActivity implements ConversationMessageAdapter.Host {
    public static final String EXTRA_THREAD_ID = "thread_id";
    public static final String EXTRA_ADDRESS = "address";
    public static final String EXTRA_NEW_COMPOSE = "new_compose";

    private static final int PICK_CONTACT = 2001;
    private static final int MENU_OPTIONS = 1;
    private static final int MENU_ARCHIVE = 2;
    private static final int MENU_DELETE = 3;

    private long threadId = -1L;
    private String address;
    private LinearLayout recipientContainer;
    private AutoCompleteTextView recipient;
    private Button pickContact;
    private RecyclerView messageList;
    private ConversationMessageAdapter adapter;
    private EditText compose;
    private ImageButton send;
    private ContentObserver observer;
    private int generation;
    private boolean restoringDraft;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        parseIntent(getIntent());
        setContentView(R.layout.conversation_activity);
        bindViews();
        configureDarkUi();
        configureActionBar();
        configureRecipient();
        configureComposer();
        configureMessages();
        restoreDraft();
        registerObserver();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        saveDraft();
        parseIntent(intent);
        configureActionBar();
        configureRecipient();
        adapter = new ConversationMessageAdapter(address, this);
        messageList.setAdapter(adapter);
        restoreDraft();
        loadMessages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (threadId >= 0 && SmsRole.hasCore(this)) {
            new Thread(() -> {
                try { SmsRepository.markRead(this, threadId); } catch (Exception ignored) { }
            }, "sms-mark-read").start();
            NotificationHelper.cancel(this, threadId);
            loadMessages();
        }
    }

    @Override
    protected void onPause() {
        saveDraft();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (observer != null) {
            try { getContentResolver().unregisterContentObserver(observer); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    private void parseIntent(Intent intent) {
        threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L);
        address = intent.getStringExtra(EXTRA_ADDRESS);
        Uri data = intent.getData();
        if (data == null) return;
        String scheme = data.getScheme();
        if (!"sms".equalsIgnoreCase(scheme) && !"smsto".equalsIgnoreCase(scheme)
                && !"mms".equalsIgnoreCase(scheme) && !"mmsto".equalsIgnoreCase(scheme)) return;
        String raw = data.getSchemeSpecificPart();
        if (raw == null) return;
        int q = raw.indexOf('?');
        if (q >= 0) raw = raw.substring(0, q);
        if (!raw.trim().isEmpty()) address = raw.trim();
    }

    private void bindViews() {
        recipientContainer = findViewById(R.id.recipient_container);
        recipient = findViewById(R.id.recipient_text_view);
        pickContact = findViewById(R.id.pick_contact);
        messageList = findViewById(R.id.message_list);
        ComposeMessageView composeView = findViewById(R.id.message_compose_view);
        compose = composeView.findViewById(R.id.compose_message_text);
        send = composeView.findViewById(R.id.send_message_button);
    }


    private void configureDarkUi() {
        boolean dark = AppPrefs.isDark(this);
        int surface = AppPrefs.background(this);
        int panel = dark ? 0xff2b2b2b : 0xffffffff;
        int primary = dark ? 0xfff5f5f5 : 0xde000000;
        int secondary = dark ? 0xffbdbdbd : 0x8a000000;

        View root = findViewById(R.id.conversation_root);
        if (root != null) root.setBackgroundColor(surface);
        messageList.setBackgroundColor(surface);

        recipientContainer.setBackgroundColor(panel);
        recipient.setTextColor(primary);
        recipient.setHintTextColor(secondary);
        pickContact.setTextColor(primary);
        TextView toLabel = findViewById(R.id.recipient_to_label);
        if (toLabel != null) toLabel.setTextColor(secondary);

        ComposeMessageView composeView = findViewById(R.id.message_compose_view);
        composeView.setBackgroundColor(panel);
        compose.setTextColor(primary);
        compose.setHintTextColor(secondary);
        TextView counter = composeView.findViewById(R.id.message_text_counter);
        if (counter != null) counter.setTextColor(secondary);
    }

    private void configureActionBar() {
        ActionBar bar = getSupportActionBar();
        if (bar == null) return;
        bar.setDisplayHomeAsUpEnabled(true);
        int color = address == null ? AppPrefs.accent(this) : AppPrefs.conversationColor(this, address);
        bar.setBackgroundDrawable(new ColorDrawable(color));
        if (address == null || address.trim().isEmpty()) {
            bar.setTitle("New conversation");
            bar.setSubtitle(null);
        } else {
            String name = ContactResolver.name(this, address);
            bar.setTitle(name);
            bar.setSubtitle(name.equals(address) ? null : address);
        }
    }

    private void configureRecipient() {
        boolean newCompose = threadId < 0 && (address == null || address.trim().isEmpty());
        recipientContainer.setVisibility(newCompose ? View.VISIBLE : View.GONE);
        if (newCompose) {
            recipient.setText("");
            recipient.setHint("Enter name or number");
            if (!AppPrefs.keyboardLearning(this) && Build.VERSION.SDK_INT >= 26) {
                recipient.setImeOptions(recipient.getImeOptions() | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
                recipient.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            }
        }
        pickContact.setOnClickListener(v -> pickContact());
    }

    private void configureComposer() {
        if (!AppPrefs.keyboardLearning(this) && Build.VERSION.SDK_INT >= 26) {
            compose.setImeOptions(compose.getImeOptions() | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
            compose.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }
        send.setColorFilter(AppPrefs.conversationColor(this, address));
        send.setOnClickListener(v -> sendMessage());
        compose.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                send.setEnabled(s != null && s.toString().trim().length() > 0);
                if (!restoringDraft) saveDraft();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void configureMessages() {
        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setStackFromEnd(true);
        messageList.setLayoutManager(manager);
        adapter = new ConversationMessageAdapter(address, this);
        messageList.setAdapter(adapter);
    }

    private void registerObserver() {
        observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) { if (threadId >= 0) loadMessages(); }
        };
        getContentResolver().registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer);
    }

    private void pickContact() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, SmsRole.REQ_PERMS);
            return;
        }
        Intent i = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        startActivityForResult(i, PICK_CONTACT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_CONTACT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Cursor c = null;
        try {
            c = getContentResolver().query(data.getData(),
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
            if (c != null && c.moveToFirst()) recipient.setText(c.getString(0));
        } finally { if (c != null) c.close(); }
    }

    private void sendMessage() {
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            SmsRole.permissions(this);
            return;
        }
        String destination = recipientContainer.getVisibility() == View.VISIBLE
                ? recipient.getText().toString().trim() : address;
        final String body = compose.getText().toString();
        if (destination == null || destination.trim().isEmpty()) {
            Toast.makeText(this, "Enter a recipient", Toast.LENGTH_SHORT).show();
            return;
        }
        if (body.trim().isEmpty()) return;

        final String dest = destination;
        final String oldDraftKey = AppPrefs.draftKey(threadId, address);
        send.setEnabled(false);
        compose.setEnabled(false);
        new Thread(() -> {
            try {
                long id = SmsSender.send(this, dest, body);
                runOnUiThread(() -> {
                    threadId = id;
                    address = dest;
                    recipientContainer.setVisibility(View.GONE);
                    restoringDraft = true;
                    compose.setText("");
                    restoringDraft = false;
                    AppPrefs.p(this).edit().remove(oldDraftKey).apply();
                    compose.setEnabled(true);
                    send.setEnabled(false);
                    adapter = new ConversationMessageAdapter(address, this);
                    messageList.setAdapter(adapter);
                    configureActionBar();
                    if (AppPrefs.sendSound(this)) playSendSound();
                    loadMessages();
                    invalidateOptionsMenu();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    compose.setEnabled(true);
                    send.setEnabled(true);
                    Toast.makeText(this, "Message could not be sent", Toast.LENGTH_LONG).show();
                });
            }
        }, "sms-send").start();
    }

    private void playSendSound() {
        try {
            final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_SYSTEM, 45);
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 75);
            new Handler(Looper.getMainLooper()).postDelayed(tone::release, 160);
        } catch (Exception ignored) { }
    }

    private void loadMessages() {
        if (threadId < 0 || !SmsRole.hasCore(this)) return;
        final int g = ++generation;
        new Thread(() -> {
            List<SmsRepository.Message> data = null;
            try { data = SmsRepository.messages(this, threadId); } catch (Exception ignored) { }
            final List<SmsRepository.Message> out = data;
            runOnUiThread(() -> {
                if (g != generation) return;
                adapter.setItems(out);
                if (adapter.getItemCount() > 0) messageList.scrollToPosition(adapter.getItemCount() - 1);
            });
        }, "sms-thread").start();
    }

    @Override
    public void menu(SmsRepository.Message message) {
        new AlertDialog.Builder(this)
                .setItems(new String[]{"Copy text", "Delete message"}, (dialog, which) -> {
                    if (which == 0) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("SMS", message.body));
                    } else {
                        Runnable remove = () -> new Thread(() -> {
                            try { SmsRepository.deleteMessage(this, message.id); } catch (Exception ignored) { }
                            runOnUiThread(this::loadMessages);
                        }, "delete-message").start();
                        if (AppPrefs.confirmDelete(this)) {
                            new AlertDialog.Builder(this).setTitle("Delete message?")
                                    .setPositiveButton("Delete", (d, w) -> remove.run())
                                    .setNegativeButton("Cancel", null).show();
                        } else remove.run();
                    }
                }).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (address != null && !address.trim().isEmpty()) {
            menu.add(0, MENU_OPTIONS, 0, "People & options");
            menu.add(0, MENU_ARCHIVE, 1, AppPrefs.isArchived(this, threadId) ? "Unarchive" : "Archive");
            if (threadId >= 0) menu.add(0, MENU_DELETE, 2, "Delete conversation");
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        if (item.getItemId() == MENU_OPTIONS) {
            startActivity(new Intent(this, PeopleAndOptionsActivity.class)
                    .putExtra(PeopleAndOptionsActivity.EXTRA_THREAD_ID, threadId)
                    .putExtra(PeopleAndOptionsActivity.EXTRA_ADDRESS, address));
            return true;
        }
        if (item.getItemId() == MENU_ARCHIVE) {
            if (threadId >= 0) AppPrefs.archive(this, threadId, !AppPrefs.isArchived(this, threadId));
            finish(); return true;
        }
        if (item.getItemId() == MENU_DELETE) { confirmDeleteConversation(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void confirmDeleteConversation() {
        Runnable remove = () -> new Thread(() -> {
            try { SmsRepository.deleteThread(this, threadId); } catch (Exception ignored) { }
            runOnUiThread(this::finish);
        }, "delete-thread").start();
        if (!AppPrefs.confirmDelete(this)) { remove.run(); return; }
        new AlertDialog.Builder(this).setTitle("Delete conversation?")
                .setMessage("This permanently deletes its SMS messages from this device.")
                .setPositiveButton("Delete", (d, w) -> remove.run())
                .setNegativeButton("Cancel", null).show();
    }

    private void restoreDraft() {
        String prefill = getIntent().getStringExtra("sms_body");
        if (prefill == null) prefill = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        String value = prefill != null ? prefill : AppPrefs.p(this).getString(AppPrefs.draftKey(threadId, address), "");
        restoringDraft = true;
        compose.setText(value == null ? "" : value);
        if (compose.length() > 0) compose.setSelection(compose.length());
        restoringDraft = false;
    }

    private void saveDraft() {
        if (compose == null || restoringDraft) return;
        String key = AppPrefs.draftKey(threadId, address);
        String value = compose.getText().toString();
        if (value.trim().isEmpty()) AppPrefs.p(this).edit().remove(key).apply();
        else AppPrefs.p(this).edit().putString(key, value).apply();
    }
}
