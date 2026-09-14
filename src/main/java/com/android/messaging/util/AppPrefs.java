package com.android.messaging.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.telephony.PhoneNumberUtils;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class AppPrefs {
    private static final String P="settings", ARCH="archived";
    public static final String NONE="none", ARCHIVE="archive", DELETE="delete", READ="read", SILENT="__silent__";
    private AppPrefs() {}
    public static SharedPreferences p(Context c){return c.getSharedPreferences(P,Context.MODE_PRIVATE);}
    public static void setInt(Context c,String k,int v){p(c).edit().putInt(k,v).apply();}
    public static void setString(Context c,String k,String v){p(c).edit().putString(k,v).apply();}
    public static void setBool(Context c,String k,boolean v){p(c).edit().putBoolean(k,v).apply();}
    private static String key(String a){String v=a==null?"unknown":a.trim().toLowerCase(Locale.US);String n=PhoneNumberUtils.normalizeNumber(v);if(n!=null&&!n.isEmpty())v=n;return Integer.toHexString(v.hashCode());}
    public static int accent(Context c){return p(c).getInt("accent",0xff039be5);}
    public static int incoming(Context c){return p(c).getInt("incoming",isDark(c)?0xff424242:0xffffffff);}
    public static int outgoing(Context c){return p(c).getInt("outgoing",accent(c));}
    public static int background(Context c){return p(c).getInt("background",isDark(c)?0xff212121:0xffeeeeee);}
    public static int scale(Context c){return p(c).getInt("scale",100);}
    public static String theme(Context c){return p(c).getString("theme","system");}
    public static String swipeLeft(Context c){return p(c).getString("swipe_left",ARCHIVE);}
    public static String swipeRight(Context c){return p(c).getString("swipe_right",READ);}
    public static boolean notifications(Context c){return p(c).getBoolean("notifications",true);}
    public static boolean preview(Context c){return p(c).getBoolean("preview",true);}
    public static boolean vibrate(Context c){return p(c).getBoolean("vibrate",true);}
    public static boolean deliveryReports(Context c){return p(c).getBoolean("delivery",false);}
    public static boolean sendSound(Context c){return p(c).getBoolean("send_sound",true);}
    public static boolean confirmDelete(Context c){return p(c).getBoolean("confirm_delete",true);}
    public static boolean screenshots(Context c){return p(c).getBoolean("screenshots",false);}
    public static boolean keyboardLearning(Context c){return p(c).getBoolean("keyboard_learning",false);}
    public static boolean isArchived(Context c,long id){return p(c).getStringSet(ARCH,new HashSet<String>()).contains(Long.toString(id));}
    public static void archive(Context c,long id,boolean on){Set<String>s=new HashSet<>(p(c).getStringSet(ARCH,new HashSet<String>()));String v=Long.toString(id);if(on)s.add(v);else s.remove(v);p(c).edit().putStringSet(ARCH,s).apply();}
    public static int conversationColor(Context c,String a){return p(c).getInt("color_"+key(a),outgoing(c));}
    public static void setConversationColor(Context c,String a,int color){p(c).edit().putInt("color_"+key(a),color).apply();}
    public static void clearConversationColor(Context c,String a){p(c).edit().remove("color_"+key(a)).apply();}
    public static boolean muted(Context c,String a){return p(c).getBoolean("mute_"+key(a),false);}
    public static void setMuted(Context c,String a,boolean v){p(c).edit().putBoolean("mute_"+key(a),v).apply();}
    public static void setContactTone(Context c,String a,String v){p(c).edit().putString("tone_"+key(a),v).apply();}
    public static void clearContactTone(Context c,String a){p(c).edit().remove("tone_"+key(a)).apply();}
    public static boolean hasContactTone(Context c,String a){return p(c).contains("tone_"+key(a));}
    public static Uri defaultTone(Context c){String v=p(c).getString("tone_default",null);return tone(v);}
    public static Uri tone(Context c,String a){String v=hasContactTone(c,a)?p(c).getString("tone_"+key(a),null):p(c).getString("tone_default",null);return tone(v);}
    private static Uri tone(String v){if(SILENT.equals(v))return null;if(v==null||v.isEmpty())return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);try{return Uri.parse(v);}catch(Exception e){return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);}}
    public static String draftKey(long id,String a){return id>=0?"draft_"+id:"draft_a_"+key(a);}
    public static boolean isDark(Context c){String t=theme(c);if("dark".equals(t))return true;if("light".equals(t))return false;return (c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
}
