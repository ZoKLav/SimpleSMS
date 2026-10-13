package com.android.messaging.ui.conversation;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipDescription;
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
import android.os.PersistableBundle;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.android.messaging.R;
import com.android.messaging.sms.SmsRepository;
import com.android.messaging.sms.SmsRepository.Message;
import com.android.messaging.sms.SmsRole;
import com.android.messaging.sms.SmsSender;
import com.android.messaging.storage.EncryptedSmsStore;
import com.android.messaging.ui.conversationsettings.PeopleAndOptionsActivity;
import com.android.messaging.util.AppPrefs;
import com.android.messaging.util.BaseActivity;
import com.android.messaging.util.ContactResolver;
import com.android.messaging.util.MessageTimes;
import com.android.messaging.util.NotificationHelper;
import com.android.messaging.util.UiUtil;
import com.android.messaging.util.Workers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/** Local SMS conversation with encrypted drafts and explicit timestamp details. */
public final class ConversationActivity extends BaseActivity implements ConversationMessageAdapter.Host {
    public static final String EXTRA_THREAD_ID = "thread_id", EXTRA_ADDRESS = "address", EXTRA_NEW_COMPOSE = "new_compose";
    private static final int PICK_CONTACT = 2001, MENU_OPTIONS = 1, MENU_ARCHIVE = 2, MENU_DELETE = 3;
    private long threadId = -1;
    private String address;
    private LinearLayout recipientContainer;
    private AutoCompleteTextView recipient;
    private Button pickContact;
    private RecyclerView messageList;
    private ConversationMessageAdapter adapter;
    private EditText compose;
    private ImageButton send;
    private TextView counter;
    private ContentObserver observer;
    private volatile int generation;
    private int conversationVersion, draftRevision, recipientRevision;
    private boolean restoring, started, sending, pendingContactPick, prefillConsumed;
    private volatile boolean viewing;
    private ActionMode selectionMode;
    private final LinkedHashMap<Long, Message> selection = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveLater = this::saveDraft;
    private final Runnable changed = this::loadMessages;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.conversation_activity);
        recipientContainer = findViewById(R.id.recipient_container);
        recipient = findViewById(R.id.recipient_text_view); pickContact = findViewById(R.id.pick_contact);
        messageList = findViewById(R.id.message_list);
        ComposeMessageView composer = findViewById(R.id.message_compose_view);
        compose = composer.findViewById(R.id.compose_message_text); send = composer.findViewById(R.id.send_message_button);
        counter = composer.findViewById(R.id.message_text_counter);
        LinearLayoutManager manager = new LinearLayoutManager(this); manager.setStackFromEnd(true); messageList.setLayoutManager(manager);
        configureAppearance(); configureComposer();
        pickContact.setOnClickListener(v -> pickContact());
        recipient.setThreshold(1);
        recipient.setOnItemClickListener((parent, view, position, id) -> {
            Object value = parent.getItemAtPosition(position);
            if (value instanceof ContactResolver.Recipient) recipient.setText(((ContactResolver.Recipient) value).number);
        });
        recipient.addTextChangedListener(watcher(() -> { recipientRevision++; scheduleDraft(); }));
        beginConversation(getIntent(), state);
    }
    private TextWatcher watcher(Runnable changed) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { if (!restoring) changed.run(); }
            @Override public void afterTextChanged(Editable s) { }
        };
    }
    private void configureAppearance() {
        boolean dark = AppPrefs.isDark(this);
        int primary = dark ? 0xfff5f5f5 : 0xde000000, secondary = dark ? 0xffbdbdbd : 0x8a000000;
        int panel = dark ? 0xff2b2b2b : 0xffffffff;
        findViewById(R.id.conversation_root).setBackgroundColor(AppPrefs.background(this));
        messageList.setBackgroundColor(AppPrefs.background(this)); recipientContainer.setBackgroundColor(panel);
        recipient.setTextColor(primary); recipient.setHintTextColor(secondary); pickContact.setTextColor(primary);
        ((TextView) findViewById(R.id.recipient_to_label)).setTextColor(secondary);
        findViewById(R.id.message_compose_view).setBackgroundColor(panel);
        android.graphics.drawable.GradientDrawable field = new android.graphics.drawable.GradientDrawable();
        field.setColor(dark ? 0xff383c43 : 0xfff0f2f5); field.setCornerRadius(UiUtil.dp(this, 24));
        compose.setBackground(field); compose.setTextColor(primary); compose.setHintTextColor(secondary); counter.setTextColor(secondary);
        for (EditText fieldView : new EditText[]{compose, recipient}) {
            fieldView.setSaveEnabled(false);
            if (!AppPrefs.keyboardLearning(this)) fieldView.setImeOptions(fieldView.getImeOptions() | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
            if (Build.VERSION.SDK_INT >= 26) fieldView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }
    }
    private void configureComposer() {
        send.setOnClickListener(v -> sendMessage()); updateComposer();
        compose.addTextChangedListener(watcher(() -> { draftRevision++; updateComposer(); scheduleDraft(); }));
    }
    private void updateComposer() {
        send.setEnabled(!sending && compose.length() > 0 && !compose.getText().toString().trim().isEmpty());
        send.setAlpha(send.isEnabled() ? 1f : .35f);
        if (compose.length() > 0) {
            int[] length = SmsMessage.calculateLength(compose.getText(), false);
            counter.setText(length[2] + " / " + length[0]); counter.setVisibility(View.VISIBLE);
        } else counter.setVisibility(View.GONE);
    }
    private void beginConversation(Intent intent, Bundle state) {
        conversationVersion++; generation++; draftRevision++; recipientRevision++;
        if (selectionMode != null) selectionMode.finish();
        threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1); address = intent.getStringExtra(EXTRA_ADDRESS);
        Uri data = intent.getData();
        if (data != null && ("sms".equalsIgnoreCase(data.getScheme()) || "smsto".equalsIgnoreCase(data.getScheme())
                || "mms".equalsIgnoreCase(data.getScheme()) || "mmsto".equalsIgnoreCase(data.getScheme()))) {
            String raw = data.getSchemeSpecificPart();
            if (raw != null) { int q = raw.indexOf('?'); if (q >= 0) raw = raw.substring(0, q); if (!raw.trim().isEmpty()) address = raw.trim(); }
        }
        prefillConsumed = state != null && state.getBoolean("prefill_consumed");
        pendingContactPick = state != null && state.getBoolean("pending_contact_pick");
        if (state != null) { threadId = state.getLong(EXTRA_THREAD_ID, threadId); address = state.getString(EXTRA_ADDRESS, address); }
        final String prefill = prefillConsumed ? null : intent.hasExtra("sms_body") ? intent.getStringExtra("sms_body") : intent.getStringExtra(Intent.EXTRA_TEXT);
        prefillConsumed = true;
        restoring = true; compose.setText(prefill == null ? "" : prefill); recipient.setText(""); restoring = false;
        sending = false; compose.setEnabled(true); updateComposer();
        recipientContainer.setVisibility(address == null || address.trim().isEmpty() ? View.VISIBLE : View.GONE);
        adapter = new ConversationMessageAdapter(address, this); messageList.setAdapter(adapter);
        configureHeader(ContactResolver.cachedName(this, address));
        final int version = conversationVersion, bodyRevision = draftRevision, toRevision = recipientRevision;
        final long initialThread = threadId; final String initialAddress = address;
        Workers.IO.execute(() -> {
            try {
                EncryptedSmsStore store = EncryptedSmsStore.get(this);
                long resolved = initialThread;
                if (resolved < 0 && initialAddress != null && !initialAddress.trim().isEmpty()) resolved = store.threadFor(initialAddress);
                final long id = resolved;
                String draft = store.draft(draftKey(id));
                String to = id < 0 ? store.draft("draft_new_recipient") : "";
                runOnUiThread(() -> {
                    if (version != conversationVersion || isDestroyed()) return;
                    threadId = id;
                    restoring = true;
                    if (prefill == null && bodyRevision == draftRevision) { compose.setText(draft); compose.setSelection(compose.length()); }
                    if (toRevision == recipientRevision && id < 0) recipient.setText(to);
                    restoring = false; updateComposer(); loadMessages(); invalidateOptionsMenu();
                });
            } catch (Exception e) { runOnUiThread(() -> { if (version == conversationVersion) toast("Encrypted drafts could not be opened"); }); }
        });
    }
    private static String draftKey(long id) { return id < 0 ? "draft_new_message" : "draft_" + id; }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); saveDraft(); setIntent(intent); beginConversation(intent, null); refreshContactsAndHeader();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        saveDraft(); state.putLong(EXTRA_THREAD_ID, threadId); state.putString(EXTRA_ADDRESS, address);
        state.putBoolean("prefill_consumed", true); state.putBoolean("pending_contact_pick", pendingContactPick);
        super.onSaveInstanceState(state);
    }
    @Override protected void onStart() { super.onStart(); started = true; SmsRepository.listen(changed); observeSystem(); }
    private void observeSystem() {
        if (!started || observer != null || !SmsRepository.canReadSystem(this)) return;
        ContentObserver next = new ContentObserver(handler) { @Override public void onChange(boolean self) { loadMessages(); } };
        try { getContentResolver().registerContentObserver(Telephony.Sms.CONTENT_URI, true, next); observer = next; }
        catch (SecurityException ignored) { }
    }
    @Override protected void onResume() { super.onResume(); viewing = hasWindowFocus(); observeSystem(); loadMessages(); }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus); viewing = focus && started;
        if (viewing) loadMessages();
    }
    @Override protected void onPause() { viewing = false; saveDraft(); super.onPause(); }
    @Override protected void onStop() {
        started = false; viewing = false; generation++; SmsRepository.unlisten(changed);
        if (observer != null) { getContentResolver().unregisterContentObserver(observer); observer = null; }
        super.onStop();
    }
    @Override protected void onDestroy() { handler.removeCallbacks(saveLater); super.onDestroy(); }
    @Override protected void onContactsChanged() { refreshContactsAndHeader(); loadMessages(); }
    private void refreshContactsAndHeader() {
        if (recipient == null || !started) return;
        final int version = conversationVersion; final String currentAddress = address;
        Workers.IO.execute(() -> {
            List<ContactResolver.Recipient> contacts = ContactResolver.recipients(this);
            String name = ContactResolver.name(this, currentAddress);
            runOnUiThread(() -> {
                if (version != conversationVersion || !started) return;
                recipient.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, contacts));
                configureHeader(name); adapter.notifyDataSetChanged();
            });
        });
    }
    private void configureHeader(String name) {
        ActionBar bar = getSupportActionBar();
        if (bar == null) return;
        bar.setDisplayHomeAsUpEnabled(true);
        bar.setBackgroundDrawable(new ColorDrawable(address == null ? AppPrefs.accent(this) : AppPrefs.conversationColor(this, address)));
        bar.setTitle(address == null || address.trim().isEmpty() ? "New conversation" : name);
        bar.setSubtitle(address == null || address.equals(name) ? null : address);
        send.setColorFilter(AppPrefs.conversationColor(this, address));
    }
    private void pickContact() {
        if (!ContactResolver.allowed(this)) {
            pendingContactPick = true; requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, SmsRole.REQ_PERMS); return;
        }
        pendingContactPick = false;
        try { startActivityForResult(new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), PICK_CONTACT); }
        catch (android.content.ActivityNotFoundException e) { toast("No contacts picker is installed; enter a phone number"); }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results); observeSystem(); loadMessages();
        if (pendingContactPick) { pendingContactPick = false; if (ContactResolver.allowed(this)) pickContact(); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_CONTACT || result != RESULT_OK || data == null || data.getData() == null) return;
        try (Cursor x = getContentResolver().query(data.getData(), new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null)) {
            if (x != null && x.moveToFirst()) recipient.setText(x.getString(0));
        } catch (Exception e) { toast("Could not read the selected phone number"); }
    }
    private void scheduleDraft() { handler.removeCallbacks(saveLater); handler.postDelayed(saveLater, 300); }
    private void saveDraft() {
        handler.removeCallbacks(saveLater);
        if (compose == null || restoring || sending) return;
        final String body = compose.getText().toString(), to = recipient.getText().toString(), currentAddress = address;
        final long currentThread = threadId;
        Workers.IO.execute(() -> {
            try {
                EncryptedSmsStore store = EncryptedSmsStore.get(this);
                long id = currentThread;
                if (id < 0 && currentAddress != null && !currentAddress.trim().isEmpty()) id = store.threadFor(currentAddress);
                store.putDraft(draftKey(id), body);
                if (id < 0) store.putDraft("draft_new_recipient", to);
            } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed()) toast("Draft could not be saved to encrypted storage"); }); }
        });
    }
    private void sendMessage() {
        if (sending) return;
        if (!SmsRole.isDefault(this)) { SmsRole.request(this); return; }
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) { SmsRole.permissions(this); return; }
        String dest = recipientContainer.getVisibility() == View.VISIBLE ? recipient.getText().toString().trim() : address;
        String body = compose.getText().toString();
        if (dest == null || dest.trim().isEmpty()) { toast("Enter one recipient"); return; }
        if (body.trim().isEmpty()) return;
        handler.removeCallbacks(saveLater); sending = true; compose.setEnabled(false); updateComposer();
        final int version = conversationVersion; final String oldDraft = draftKey(threadId);
        Workers.IO.execute(() -> {
            try {
                long id = SmsSender.send(this, dest, body);
                EncryptedSmsStore.get(this).putDraft(oldDraft, "");
                EncryptedSmsStore.get(this).putDraft(draftKey(id), "");
                if ("draft_new_message".equals(oldDraft)) EncryptedSmsStore.get(this).putDraft("draft_new_recipient", "");
                runOnUiThread(() -> {
                    if (version != conversationVersion || isDestroyed()) return;
                    sending = false; threadId = id; address = dest; restoring = true; compose.setText(""); restoring = false;
                    compose.setEnabled(true); updateComposer(); recipientContainer.setVisibility(View.GONE);
                    adapter = new ConversationMessageAdapter(address, this); messageList.setAdapter(adapter);
                    refreshContactsAndHeader(); loadMessages(); invalidateOptionsMenu();
                    if (AppPrefs.sendSound(this)) playSendSound();
                });
            } catch (Exception e) {
                String text = e instanceof IllegalArgumentException || e instanceof IllegalStateException || e instanceof SecurityException
                        ? e.getMessage() : "Message could not be sent";
                runOnUiThread(() -> {
                    if (version != conversationVersion || isDestroyed()) return;
                    sending = false; compose.setEnabled(true); updateComposer(); toast(text == null ? "Message could not be sent" : text); saveDraft(); loadMessages();
                });
            }
        });
    }
    private void playSendSound() {
        try { ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_SYSTEM, 45); tone.startTone(ToneGenerator.TONE_PROP_ACK, 75); handler.postDelayed(tone::release, 160); }
        catch (Exception ignored) { }
    }
    private void loadMessages() {
        if (!started || threadId < 0 || adapter == null) return;
        final int g = ++generation; final long currentThread = threadId;
        LinearLayoutManager manager = (LinearLayoutManager) messageList.getLayoutManager();
        final boolean atBottom = adapter.getItemCount() == 0 || manager == null || manager.findLastVisibleItemPosition() >= adapter.getItemCount() - 2;
        Workers.IO.execute(() -> {
            if (g != generation) return;
            try {
                if (viewing) { SmsRepository.markRead(this, currentThread, false); NotificationHelper.cancel(this, currentThread); }
                List<Message> data = SmsRepository.messages(this, currentThread);
                String resolvedAddress = address;
                if (resolvedAddress == null && !data.isEmpty()) resolvedAddress = data.get(0).address;
                final String a = resolvedAddress, name = ContactResolver.name(this, resolvedAddress);
                runOnUiThread(() -> {
                    if (g != generation || !started) return;
                    if (address == null && a != null) { address = a; recipientContainer.setVisibility(View.GONE); configureHeader(name); invalidateOptionsMenu(); }
                    adapter.setItems(data);
                    if (atBottom && adapter.getItemCount() > 0) messageList.scrollToPosition(adapter.getItemCount() - 1);
                });
            } catch (Exception e) { runOnUiThread(() -> { if (g == generation && started) toast("Messages could not be opened. Check storage and SMS access."); }); }
        });
    }
    @Override public boolean selected(long id) { return selection.containsKey(id); }
    @Override public void click(Message m) { if (selectionMode != null) toggleSelection(m); }
    @Override public void menu(Message m) {
        if (selectionMode != null) { toggleSelection(m); return; }
        String[] choices = m.type == Telephony.Sms.MESSAGE_TYPE_FAILED
                ? new String[]{"Copy text", "Message details", "Select messages", "Delete message", "Retry sending"}
                : new String[]{"Copy text", "Message details", "Select messages", "Delete message"};
        new AlertDialog.Builder(this).setItems(choices, (d, which) -> {
            if (which == 0) copy(m.body);
            else if (which == 1) details(m);
            else if (which == 2) toggleSelection(m);
            else if (which == 3) deleteMessages(Collections.singletonList(m));
            else retry(m);
        }).show();
    }
    private void details(Message m) {
        boolean incoming = m.type == Telephony.Sms.MESSAGE_TYPE_INBOX;
        boolean clock = DateFormat.is24HourFormat(this);
        String text = "Number: " + m.address + "\n\n" + (incoming ? "Carrier sent time: " : "Send confirmation on this phone: ")
                + MessageTimes.exact(m.dateSent, clock) + "\n" + (incoming ? "Received on this phone: " : "Queued/recorded on this phone: ")
                + MessageTimes.exact(m.date, clock) + "\nDelivery report received: " + MessageTimes.exact(m.dateDelivered, clock)
                + "\n\nStorage: " + (m.encrypted ? "Encrypted private storage" : "Android shared SMS storage");
        if (m.type == Telephony.Sms.MESSAGE_TYPE_FAILED) text += "\nSend failed (code " + m.error + ")";
        new AlertDialog.Builder(this).setTitle("Message details").setMessage(text).setPositiveButton("OK", null).show();
    }
    private void copy(String text) {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class); if (clipboard == null) return;
        ClipData data = ClipData.newPlainText("SMS", text);
        if (Build.VERSION.SDK_INT >= 33) { PersistableBundle extras = new PersistableBundle(); extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true); data.getDescription().setExtras(extras); }
        clipboard.setPrimaryClip(data); toast("Copied");
    }
    private void toggleSelection(Message m) {
        if (selectionMode == null) selectionMode = startSupportActionMode(new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) { menu.add(0, 1, 0, "Copy"); menu.add(0, 2, 1, "Delete"); return true; }
            @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }
            @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                ArrayList<Message> selected = new ArrayList<>(selection.values()); selected.sort((a, b) -> Long.compare(a.date, b.date));
                if (item.getItemId() == 1) {
                    StringBuilder text = new StringBuilder();
                    for (Message record : selected) { if (text.length() > 0) text.append("\n\n"); text.append(MessageTimes.exact(MessageTimes.displayed(record), DateFormat.is24HourFormat(ConversationActivity.this))).append("\n").append(record.body); }
                    copy(text.toString());
                } else deleteMessages(selected);
                mode.finish(); return true;
            }
            @Override public void onDestroyActionMode(ActionMode mode) { selectionMode = null; selection.clear(); adapter.notifyDataSetChanged(); }
        });
        if (selectionMode == null) return;
        if (selection.containsKey(m.id)) selection.remove(m.id); else selection.put(m.id, m);
        if (selection.isEmpty()) selectionMode.finish();
        else { selectionMode.setTitle(selection.size() + " selected"); adapter.notifyDataSetChanged(); }
    }
    private void deleteMessages(List<Message> records) {
        Runnable remove = () -> Workers.IO.execute(() -> {
            try { for (Message m : records) SmsRepository.deleteMessage(this, m); }
            catch (Exception e) { runOnUiThread(() -> toast("Delete failed; check the default SMS app setting")); }
            runOnUiThread(this::loadMessages);
        });
        if (AppPrefs.confirmDelete(this)) new AlertDialog.Builder(this).setTitle("Delete " + records.size() + " message(s)?")
                .setPositiveButton("Delete", (d, w) -> remove.run()).setNegativeButton("Cancel", null).show();
        else remove.run();
    }
    private void retry(Message m) {
        new AlertDialog.Builder(this).setTitle("Retry sending?").setMessage("Some parts of a failed multipart SMS may already have arrived. Retrying sends a new copy.")
                .setPositiveButton("Retry", (d, w) -> Workers.IO.execute(() -> {
                    try { SmsSender.send(this, m.address, m.body); }
                    catch (Exception e) { runOnUiThread(() -> toast("Retry failed; check SMS permissions and the default SIM")); }
                    runOnUiThread(this::loadMessages);
                })).setNegativeButton("Cancel", null).show();
    }
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        if (address != null && !address.trim().isEmpty()) {
            menu.add(0, MENU_OPTIONS, 0, "People & options");
            if (threadId >= 0) { menu.add(0, MENU_ARCHIVE, 1, AppPrefs.isArchived(this, threadId) ? "Unarchive" : "Archive"); menu.add(0, MENU_DELETE, 2, "Delete conversation"); }
        }
        return true;
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        if (item.getItemId() == MENU_OPTIONS) { startActivity(new Intent(this, PeopleAndOptionsActivity.class).putExtra(PeopleAndOptionsActivity.EXTRA_THREAD_ID, threadId).putExtra(PeopleAndOptionsActivity.EXTRA_ADDRESS, address)); return true; }
        if (item.getItemId() == MENU_ARCHIVE) { AppPrefs.archive(this, threadId, !AppPrefs.isArchived(this, threadId)); finish(); return true; }
        if (item.getItemId() == MENU_DELETE) {
            final long id = threadId;
            Runnable remove = () -> Workers.IO.execute(() -> {
                try { SmsRepository.deleteThread(this, id); runOnUiThread(this::finish); }
                catch (Exception e) { runOnUiThread(() -> toast("Delete failed; check the default SMS app setting")); }
            });
            if (AppPrefs.confirmDelete(this)) new AlertDialog.Builder(this).setTitle("Delete conversation?").setMessage("Permanently delete its SMS messages from this device?")
                    .setPositiveButton("Delete", (d, w) -> remove.run()).setNegativeButton("Cancel", null).show();
            else remove.run();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private void toast(String text) { if (!isDestroyed()) Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
