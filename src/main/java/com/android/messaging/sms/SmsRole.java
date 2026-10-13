package com.android.messaging.sms;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.provider.Telephony;
import java.util.ArrayList;

public final class SmsRole {
    public static final int REQ_ROLE = 70, REQ_PERMS = 71;
    private SmsRole() { }
    public static boolean isDefault(Context c) {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager r = c.getSystemService(RoleManager.class);
            return r != null && r.isRoleAvailable(RoleManager.ROLE_SMS) && r.isRoleHeld(RoleManager.ROLE_SMS);
        }
        return c.getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(c));
    }
    public static void request(Activity a) {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager r = a.getSystemService(RoleManager.class);
            if (r != null && r.isRoleAvailable(RoleManager.ROLE_SMS)) {
                a.startActivityForResult(r.createRequestRoleIntent(RoleManager.ROLE_SMS), REQ_ROLE); return;
            }
        }
        Intent i = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
        i.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, a.getPackageName()); a.startActivityForResult(i, REQ_ROLE);
    }
    public static void permissions(Activity a) {
        ArrayList<String> missing = new ArrayList<>();
        add(a, missing, Manifest.permission.READ_SMS); add(a, missing, Manifest.permission.RECEIVE_SMS);
        add(a, missing, Manifest.permission.SEND_SMS); add(a, missing, Manifest.permission.READ_CONTACTS);
        if (Build.VERSION.SDK_INT >= 33) add(a, missing, Manifest.permission.POST_NOTIFICATIONS);
        if (!missing.isEmpty()) a.requestPermissions(missing.toArray(new String[0]), REQ_PERMS);
    }
    private static void add(Context c, ArrayList<String> missing, String permission) {
        if (c.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) missing.add(permission);
    }
    public static void appSettings(Activity a) {
        a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName())));
    }
}
