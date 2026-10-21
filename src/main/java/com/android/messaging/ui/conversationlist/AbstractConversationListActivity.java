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
import android.support.v7.app.ActionBar;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
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
    private RecyclerView list; private ConversationListAdapter adapter; private TextView empty; private ImageView fab;
    private ContentObserver observer;private boolean observingShared; private int generation; private boolean askedRole, askedPerms;
    protected abstract boolean archived();
    protected String titleText(){return archived()?"Archived":"SimpleSMS";}
    @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.conversation_list_activity);ActionBar ab=getSupportActionBar();if(ab!=null){ab.setTitle(titleText());int color=archived()?0xff607d8b:AppPrefs.accent(this);ab.setBackgroundDrawable(new ColorDrawable(color));}list=findViewById(R.id.conversation_list);empty=findViewById(R.id.no_conversations_view);fab=findViewById(R.id.start_new_conversation_button);int fabColor=AppPrefs.accent(this);fab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(fabColor));fab.setColorFilter(com.android.messaging.util.UiUtil.readable(fabColor));int surface=AppPrefs.isDark(this)?0xff212121:0xffffffff;list.setBackgroundColor(surface);empty.setTextColor(AppPrefs.isDark(this)?0xffbdbdbd:0x8a000000);list.setLayoutManager(new LinearLayoutManager(this));adapter=new ConversationListAdapter(this);list.setAdapter(adapter);list.addOnItemTouchListener(new ConversationListSwipeHelper(list,this::swiped));if(archived())fab.setVisibility(View.GONE);else fab.setOnClickListener(v->startActivity(new Intent(this,ConversationActivity.class).putExtra(ConversationActivity.EXTRA_NEW_COMPOSE,true)));observer=new ContentObserver(new Handler(Looper.getMainLooper())){@Override public void onChange(boolean self){reload();}};getContentResolver().registerContentObserver(com.android.messaging.security.Vault.changes(this),true,observer);observeShared();}
    private void observeShared(){if(observingShared||observer==null||!SmsRole.hasCore(this))return;try{getContentResolver().registerContentObserver(Uri.parse("content://mms-sms"),true,observer);getContentResolver().registerContentObserver(Telephony.Sms.CONTENT_URI,true,observer);getContentResolver().registerContentObserver(Telephony.Mms.CONTENT_URI,true,observer);observingShared=true;}catch(SecurityException unavailable){/* Permission may have changed during registration; retry after a grant. */}}
    @Override protected void onDestroy(){if(observer!=null)getContentResolver().unregisterContentObserver(observer);super.onDestroy();}
    @Override protected void onResume(){super.onResume();if(!com.android.messaging.security.Vault.unlocked())return;if(!SmsRole.isDefault(this)&&!askedRole){askedRole=true;new AlertDialog.Builder(this).setTitle("Use SimpleSMS for SMS?").setMessage("SimpleSMS needs to be the default SMS app to receive and store incoming texts.").setPositiveButton("Set default",(d,w)->SmsRole.request(this)).setNegativeButton("Not now",null).show();}observeShared();if(!askedPerms){askedPerms=true;SmsRole.permissions(this);}reload();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);observeShared();reload();}
    @Override public boolean onCreateOptionsMenu(Menu menu){if(!archived()){MenuItem search=menu.add("Search");search.setIcon(R.drawable.ic_search_light);search.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM|MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);SearchView sv=new SearchView(this);search.setActionView(sv);sv.setOnQueryTextListener(new SearchView.OnQueryTextListener(){public boolean onQueryTextSubmit(String q){adapter.filter(q);return true;}public boolean onQueryTextChange(String q){adapter.filter(q);return true;}});menu.add("Archived");}menu.add("Settings");return true;}
    @Override public boolean onOptionsItemSelected(MenuItem i){String s=i.getTitle()==null?"":i.getTitle().toString();if("Archived".equals(s)){startActivity(new Intent(this,ArchivedConversationListActivity.class));return true;}if("Settings".equals(s)){startActivity(new Intent(this,ApplicationSettingsActivity.class));return true;}return super.onOptionsItemSelected(i);}
    private void reload(){if(!com.android.messaging.security.Vault.unlocked())return;final int g=++generation;new Thread(()->{List<SmsRepository.Conversation>d;try{d=SmsRepository.conversations(this,archived());}catch(Exception failure){runOnUiThread(()->{if(g==generation){empty.setText("Could not read messages. Check SMS permission and try reopening the app.");empty.setVisibility(View.VISIBLE);Toast.makeText(this,"Could not read SMS/MMS: "+failure.getMessage(),Toast.LENGTH_LONG).show();}});return;}final List<SmsRepository.Conversation>out=d;runOnUiThread(()->{if(g!=generation)return;adapter.setItems(out);empty.setText(SmsRole.hasCore(this)?"No conversations":"Allow SMS access to display your existing messages.");empty.setVisibility(adapter.getItemCount()==0?View.VISIBLE:View.GONE);});},"sms-list").start();}
    @Override public void open(SmsRepository.Conversation c){startActivity(new Intent(this,ConversationActivity.class).putExtra(ConversationActivity.EXTRA_THREAD_ID,c.threadId));}
    @Override public void menu(SmsRepository.Conversation c){String ar=AppPrefs.isArchived(this,c.threadId)?"Unarchive":"Archive";new AlertDialog.Builder(this).setTitle(c.title).setItems(new String[]{ar,"Mark read","People & options","Delete"},(d,w)->{if(w==0){AppPrefs.archive(this,c.threadId,!AppPrefs.isArchived(this,c.threadId));reload();}else if(w==1){new Thread(()->{try{SmsRepository.markRead(this,c.threadId);}catch(Exception ignored){}runOnUiThread(this::reload);}).start();}else if(w==2)startActivity(new Intent(this,PeopleAndOptionsActivity.class).putExtra(PeopleAndOptionsActivity.EXTRA_THREAD_ID,c.threadId));else delete(c);}).show();}
    private void swiped(int pos,boolean left){if(pos<0||pos>=adapter.getItemCount())return;SmsRepository.Conversation c=adapter.get(pos);String a=left?AppPrefs.swipeLeft(this):AppPrefs.swipeRight(this);if(AppPrefs.ARCHIVE.equals(a)){AppPrefs.archive(this,c.threadId,!AppPrefs.isArchived(this,c.threadId));reload();}else if(AppPrefs.READ.equals(a)){new Thread(()->{try{SmsRepository.markRead(this,c.threadId);}catch(Exception ignored){}runOnUiThread(this::reload);}).start();}else if(AppPrefs.DELETE.equals(a))delete(c);}
    private void delete(SmsRepository.Conversation c){Runnable r=()->new Thread(()->{try{SmsRepository.deleteThread(this,c.threadId);}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Delete failed",Toast.LENGTH_SHORT).show());}runOnUiThread(this::reload);},"delete-thread").start();if(AppPrefs.confirmDelete(this))new AlertDialog.Builder(this).setTitle("Delete conversation?").setMessage("Deletes unlocked SMS/MMS from shared and private storage. Locked messages are kept.").setPositiveButton("Delete",(d,w)->r.run()).setNegativeButton("Cancel",null).show();else r.run();}
}
