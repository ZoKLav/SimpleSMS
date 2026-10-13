package com.android.messaging.ui.conversationlist;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.android.messaging.R;
import com.android.messaging.sms.SmsRepository;
import com.android.messaging.sms.SmsRole;
import com.android.messaging.ui.appsettings.ApplicationSettingsActivity;
import com.android.messaging.ui.conversation.ConversationActivity;
import com.android.messaging.ui.conversationsettings.PeopleAndOptionsActivity;
import com.android.messaging.util.AppPrefs;
import com.android.messaging.util.BaseActivity;
import com.android.messaging.util.Workers;
import java.util.List;

public abstract class AbstractConversationListActivity extends BaseActivity implements ConversationListAdapter.Host {
    private RecyclerView list;
    private ConversationListAdapter adapter;
    private TextView empty;
    private ContentObserver observer;
    private volatile int generation;
    private boolean started;
    private final Runnable changed = this::reload;
    protected abstract boolean archived();
    protected String titleText() { return archived() ? "Archived" : "SimpleSMS"; }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.conversation_list_activity);
        ActionBar bar = getSupportActionBar();
        if (bar != null) { bar.setTitle(titleText()); bar.setBackgroundDrawable(new ColorDrawable(archived() ? 0xff607d8b : AppPrefs.accent(this))); }
        list = findViewById(R.id.conversation_list); empty = findViewById(R.id.no_conversations_view);
        ImageView fab = findViewById(R.id.start_new_conversation_button);
        fab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(AppPrefs.accent(this)));
        fab.setColorFilter(com.android.messaging.util.UiUtil.readable(AppPrefs.accent(this)));
        list.setBackgroundColor(AppPrefs.isDark(this) ? 0xff212121 : 0xffffffff);
        empty.setTextColor(AppPrefs.isDark(this) ? 0xffbdbdbd : 0x8a000000);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ConversationListAdapter(this); list.setAdapter(adapter);
        list.addOnItemTouchListener(new ConversationListSwipeHelper(list, this::swiped));
        if (archived()) fab.setVisibility(View.GONE);
        else fab.setOnClickListener(v -> startActivity(new Intent(this, ConversationActivity.class).putExtra(ConversationActivity.EXTRA_NEW_COMPOSE, true)));
    }
    @Override protected void onStart() {
        super.onStart(); started = true; SmsRepository.listen(changed); observeSystem();
    }
    private void observeSystem() {
        if (!started || observer != null || !SmsRepository.canReadSystem(this)) return;
        ContentObserver next = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean self) { reload(); }
        };
        try { getContentResolver().registerContentObserver(Telephony.Sms.CONTENT_URI, true, next); observer = next; }
        catch (SecurityException ignored) { }
    }
    @Override protected void onStop() {
        started = false; generation++; SmsRepository.unlisten(changed);
        if (observer != null) { getContentResolver().unregisterContentObserver(observer); observer = null; }
        super.onStop();
    }
    @Override protected void onResume() {
        super.onResume();
        if (!archived() && !SmsRole.isDefault(this) && !AppPrefs.p(this).getBoolean("role_prompted", false)) {
            AppPrefs.setBool(this, "role_prompted", true);
            new AlertDialog.Builder(this).setTitle("Use SimpleSMS for SMS?")
                    .setMessage("Set it as the default SMS app to receive texts into encrypted local storage.")
                    .setPositiveButton("Set default", (d, w) -> SmsRole.request(this)).setNegativeButton("Not now", null).show();
        }
        requestInitialPermissions(); observeSystem(); reload();
    }
    private void requestInitialPermissions() {
        if (SmsRole.isDefault(this) && !AppPrefs.p(this).getBoolean("permissions_requested", false)) {
            AppPrefs.setBool(this, "permissions_requested", true); SmsRole.permissions(this);
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == SmsRole.REQ_ROLE) { requestInitialPermissions(); observeSystem(); reload(); }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results); observeSystem(); reload();
    }
    @Override protected void onContactsChanged() { reload(); }
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        if (!archived()) {
            MenuItem search = menu.add("Search"); search.setIcon(R.drawable.ic_search_light);
            search.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
            SearchView view = new SearchView(this); search.setActionView(view);
            view.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override public boolean onQueryTextSubmit(String q) { adapter.filter(q); return true; }
                @Override public boolean onQueryTextChange(String q) { adapter.filter(q); return true; }
            });
            menu.add("Archived");
        }
        menu.add("Settings"); menu.add("Default SMS app"); menu.add("Permissions"); return true;
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        String label = String.valueOf(item.getTitle());
        if ("Archived".equals(label)) startActivity(new Intent(this, ArchivedConversationListActivity.class));
        else if ("Settings".equals(label)) startActivity(new Intent(this, ApplicationSettingsActivity.class));
        else if ("Default SMS app".equals(label)) SmsRole.request(this);
        else if ("Permissions".equals(label)) SmsRole.appSettings(this);
        else return super.onOptionsItemSelected(item);
        return true;
    }
    private void reload() {
        if (!started || adapter == null) return;
        final int g = ++generation;
        Workers.IO.execute(() -> {
            if (g != generation) return;
            try {
                List<SmsRepository.Conversation> data = SmsRepository.conversations(this, archived());
                runOnUiThread(() -> {
                    if (g != generation || !started) return;
                    adapter.setItems(data);
                    empty.setText(!SmsRepository.canReadSystem(this) ? "No private messages yet. Grant SMS access to see existing history." : "No conversations yet");
                    empty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (g != generation || !started) return;
                    empty.setText("Message storage could not be opened. Check permissions and storage before deleting app data.");
                    empty.setVisibility(View.VISIBLE);
                });
            }
        });
    }
    @Override public void open(SmsRepository.Conversation c) {
        startActivity(new Intent(this, ConversationActivity.class).putExtra(ConversationActivity.EXTRA_THREAD_ID, c.threadId).putExtra(ConversationActivity.EXTRA_ADDRESS, c.address));
    }
    @Override public void menu(SmsRepository.Conversation c) {
        String archive = AppPrefs.isArchived(this, c.threadId) ? "Unarchive" : "Archive";
        new AlertDialog.Builder(this).setTitle(c.title).setItems(new String[]{archive, "Mark read", "People & options", "Delete"}, (d, which) -> {
            if (which == 0) { AppPrefs.archive(this, c.threadId, !AppPrefs.isArchived(this, c.threadId)); reload(); }
            else if (which == 1) read(c);
            else if (which == 2) startActivity(new Intent(this, PeopleAndOptionsActivity.class).putExtra(PeopleAndOptionsActivity.EXTRA_THREAD_ID, c.threadId).putExtra(PeopleAndOptionsActivity.EXTRA_ADDRESS, c.address));
            else delete(c);
        }).show();
    }
    private void read(SmsRepository.Conversation c) {
        Workers.IO.execute(() -> { try { SmsRepository.markRead(this, c.threadId); }
            catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "Could not mark messages read", Toast.LENGTH_SHORT).show()); }
            runOnUiThread(this::reload);
        });
    }
    private void swiped(int position, boolean left) {
        if (position < 0 || position >= adapter.getItemCount()) return;
        SmsRepository.Conversation c = adapter.get(position);
        String action = left ? AppPrefs.swipeLeft(this) : AppPrefs.swipeRight(this);
        if (AppPrefs.ARCHIVE.equals(action)) { AppPrefs.archive(this, c.threadId, !AppPrefs.isArchived(this, c.threadId)); reload(); }
        else if (AppPrefs.READ.equals(action)) read(c);
        else if (AppPrefs.DELETE.equals(action)) delete(c);
    }
    private void delete(SmsRepository.Conversation c) {
        Runnable action = () -> Workers.IO.execute(() -> {
            try { SmsRepository.deleteThread(this, c.threadId); }
            catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "Delete failed; check the default SMS app setting", Toast.LENGTH_LONG).show()); }
            runOnUiThread(this::reload);
        });
        if (AppPrefs.confirmDelete(this)) new AlertDialog.Builder(this).setTitle("Delete conversation?")
                .setMessage("Permanently delete its SMS messages from this device?").setPositiveButton("Delete", (d, w) -> action.run())
                .setNegativeButton("Cancel", null).show();
        else action.run();
    }
}
