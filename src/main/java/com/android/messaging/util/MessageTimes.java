package com.android.messaging.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import com.android.messaging.sms.SmsRepository.Message;

public final class MessageTimes {
    private MessageTimes() { }
    public static String exact(long time, boolean use24Hour, TimeZone zone, Locale locale) {
        if (time <= 0) return "Unavailable";
        SimpleDateFormat format = new SimpleDateFormat(use24Hour
                ? "yyyy-MM-dd HH:mm:ss XXX" : "yyyy-MM-dd h:mm:ss a XXX", locale);
        format.setTimeZone(zone); return format.format(new Date(time));
    }
    public static String exact(long time, boolean use24Hour) {
        return exact(time, use24Hour, TimeZone.getDefault(), Locale.getDefault());
    }
    public static long displayed(Message m) { return m.dateSent > 0 ? m.dateSent : m.date; }
}
