package com.android.messaging.util;
/** PDU code must never send addresses, headers or bodies to logcat. */
public final class PrivateLog {
 private PrivateLog() {}
 public static void v(String t,String s) {} public static void v(String t,String s,Throwable e) {} public static void d(String t,String s) {}
 public static void i(String t,String s) {} public static void w(String t,String s) {}
 public static void e(String t,String s) {} public static void e(String t,String s,Throwable e) {}
}
