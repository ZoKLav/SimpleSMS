package com.android.messaging.ui.conversationlist;

import android.app.AlertDialog;
import android.database.ContentObserver;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.net.Uri;
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
import android.content.Intent;
import java.util.List;

public abstract class AbstractConversationListActivity extends BaseActivity implements ConversationListAdapter.Host {
    private static final int MENU_ARCHIVED=1,MENU_SETTINGS=2,MENU_SEARCH=3;
    private RecyclerView list; private ConversationListAdapter adapter; private TextView empty; private ImageView fab;
    private ContentObserver observer;private boolean observingShared; private com.android.messaging.util.MessageLoader<List<SmsRepository.Conversation>> loader;
    private void createLoader(){final android.content.Context app=getApplicationContext();final boolean archive=archived();loader=new com.android.messaging.util.MessageLoader<>(()->SmsRepository.conversations(app,archive),this::loaded);}
    private void loaded(List<SmsRepository.Conversation> out,Exception failure){if(failure!=null){empty.setText(R.string.text_could_not_read_messages_check_sms_permission);empty.setVisibility(View.VISIBLE);return;}if(out==null)return;adapter.setItems(out,com.android.messaging.util.UiUtil.appearance(this));empty.setText(SmsRole.hasCore(this)?R.string.no_conversations:R.string.text_allow_sms_access_to_display_your_existing);}
    private boolean askedRole, askedPerms;
    @Override public void itemsChanged(){empty.setVisibility(adapter.getItemCount()==0?View.VISIBLE:View.GONE);}
    protected abstract boolean archived();
    protected String titleText(){return getString(archived()?R.string.archived:R.string.app_name);}
    @Override protected void onCreate(Bundle b){super.onCreate(b);createLoader();setContentView(R.layout.conversation_list_activity);ActionBar ab=getSupportActionBar();if(ab!=null){ab.setTitle(titleText());int color=archived()?0xff607d8b:AppPrefs.accent(this);ab.setBackgroundDrawable(new ColorDrawable(color));}list=findViewById(R.id.conversation_list);empty=findViewById(R.id.no_conversations_view);fab=findViewById(R.id.start_new_conversation_button);int fabColor=AppPrefs.accent(this);fab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(fabColor));fab.setColorFilter(com.android.messaging.util.UiUtil.readable(fabColor));int surface=AppPrefs.isDark(this)?AppPrefs.darkSurface(this,0):0xfff3f6f7;list.setBackgroundColor(surface);empty.setTextColor(AppPrefs.isDark(this)?0xffd5dce2:0x8a000000);list.setLayoutManager(new LinearLayoutManager(this));adapter=new ConversationListAdapter(this);list.setAdapter(adapter);list.addOnItemTouchListener(new ConversationListSwipeHelper(list,this::swiped));if(archived())fab.setVisibility(View.GONE);else fab.setOnClickListener(v->startActivity(new Intent(this,ConversationActivity.class).putExtra(ConversationActivity.EXTRA_NEW_COMPOSE,true)));observer=new ContentObserver(new Handler(Looper.getMainLooper())){@Override public void onChange(boolean self){reload();}};getContentResolver().registerContentObserver(com.android.messaging.security.Vault.changes(this),true,observer);observeShared();}
    private void observeShared(){if(observingShared||observer==null||!SmsRole.hasCore(this))return;try{getContentResolver().registerContentObserver(Uri.parse("content://mms-sms"),true,observer);getContentResolver().registerContentObserver(Telephony.Sms.CONTENT_URI,true,observer);getContentResolver().registerContentObserver(Telephony.Mms.CONTENT_URI,true,observer);observingShared=true;}catch(SecurityException unavailable){/* Permission may have changed during registration; retry after a grant. */}}
    @Override protected void onPause(){loader.pause();super.onPause();}
    @Override protected void onDestroy(){loader.close();adapter.close();list.setAdapter(null);if(observer!=null)getContentResolver().unregisterContentObserver(observer);super.onDestroy();}
    @Override protected void onResume(){super.onResume();if(!com.android.messaging.security.Vault.unlocked()||isFinishing())return;observeShared();loader.resume();if(!SmsRole.isDefault(this)&&!askedRole){askedRole=true;new com.android.messaging.util.ScreenDialog.Builder(this).setTitle("Use SimpleSMS for SMS?").setMessage("SimpleSMS needs to be the default SMS app to receive and store incoming texts.").setPositiveButton("Set default",(d,w)->SmsRole.request(this)).setNegativeButton("Not now",null).show();}if(!askedPerms){askedPerms=true;SmsRole.permissions(this);}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);observeShared();reload();}
    @Override public boolean onCreateOptionsMenu(Menu menu){if(!archived()){MenuItem search=menu.add(0,MENU_SEARCH,0,R.string.search);search.setIcon(R.drawable.ic_search_light);search.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM|MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);SearchView sv=new SearchView(this);search.setActionView(sv);sv.setOnQueryTextListener(new SearchView.OnQueryTextListener(){public boolean onQueryTextSubmit(String q){adapter.filter(q);return true;}public boolean onQueryTextChange(String q){adapter.filter(q);return true;}});menu.add(0,MENU_ARCHIVED,1,R.string.archived);}menu.add(0,MENU_SETTINGS,2,R.string.settings);return true;}
    @Override public boolean onOptionsItemSelected(MenuItem i){if(i.getItemId()==MENU_ARCHIVED){startActivity(new Intent(this,ArchivedConversationListActivity.class));return true;}if(i.getItemId()==MENU_SETTINGS){startActivity(new Intent(this,ApplicationSettingsActivity.class));return true;}return super.onOptionsItemSelected(i);}
    private void reload(){loader.request();}
    @Override public void open(SmsRepository.Conversation c){startActivity(new Intent(this,ConversationActivity.class).putExtra(ConversationActivity.EXTRA_THREAD_ID,c.threadId).putExtra(ConversationActivity.EXTRA_ADDRESS,c.address));}
    @Override public void menu(SmsRepository.Conversation c){String ar=AppPrefs.isArchived(this,c.threadId)?"Unarchive":"Archive";new com.android.messaging.util.ScreenDialog.Builder(this).setTitle(c.title).setItems(new String[]{ar,"Mark read","People & options","Delete"},(d,w)->{if(w==0){AppPrefs.archive(this,c.threadId,!AppPrefs.isArchived(this,c.threadId));reload();}else if(w==1){markRead(c.threadId);}else if(w==2)startActivity(new Intent(this,PeopleAndOptionsActivity.class).putExtra(PeopleAndOptionsActivity.EXTRA_THREAD_ID,c.threadId).putExtra(PeopleAndOptionsActivity.EXTRA_ADDRESS,c.address));else delete(c);}).show();}
    private void swiped(int pos,boolean left){if(pos<0||pos>=adapter.getItemCount())return;SmsRepository.Conversation c=adapter.get(pos);String a=left?AppPrefs.swipeLeft(this):AppPrefs.swipeRight(this);if(AppPrefs.ARCHIVE.equals(a)){AppPrefs.archive(this,c.threadId,!AppPrefs.isArchived(this,c.threadId));reload();}else if(AppPrefs.READ.equals(a)){markRead(c.threadId);}else if(AppPrefs.DELETE.equals(a))delete(c);}
    private void markRead(long thread){final android.content.Context app=getApplicationContext();com.android.messaging.util.UiWork.run(this,()->{SmsRepository.markRead(app,thread);return true;},(activity,value,error)->activity.reload());}
    private void delete(SmsRepository.Conversation conversation){final android.content.Context app=getApplicationContext();final long thread=conversation.threadId;Runnable remove=()->com.android.messaging.util.UiWork.run(this,()->{SmsRepository.deleteThread(app,thread);return true;},(activity,value,error)->{if(error!=null)Toast.makeText(activity,"Delete failed",Toast.LENGTH_SHORT).show();activity.reload();});if(!AppPrefs.confirmDelete(this)){remove.run();return;}new com.android.messaging.util.ScreenDialog.Builder(this).setTitle("Delete conversation?").setMessage("Deletes unlocked SMS/MMS from shared and private storage. Locked messages are kept.").setPositiveButton("Delete",(dialog,which)->remove.run()).setNegativeButton("Cancel",null).show();}
}
