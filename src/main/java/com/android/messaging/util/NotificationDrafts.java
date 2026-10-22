package com.android.messaging.util;

import android.content.Context;
import com.android.messaging.security.Vault;
import org.json.JSONObject;

/** A reply can be sealed while locked, then recovered after authentication. */
public final class NotificationDrafts {
    private NotificationDrafts(){}
    public static long save(Context c,long message,String body)throws Exception {
        return Vault.append(c,new JSONObject().put("kind","notification_reply").put("message",message).put("body",body));
    }
    public static JSONObject latest(Context c,long message)throws Exception {
        JSONObject found=null;
        for(JSONObject row:Vault.records(c))if("notification_reply".equals(row.optString("kind"))&&row.optLong("message")==message)found=row;
        return found;
    }
}
