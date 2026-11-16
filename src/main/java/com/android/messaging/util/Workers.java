package com.android.messaging.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Workers {
    private Workers() { }
    public static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> new Thread(r, "sms-storage"));
    public static final ExecutorService READ = Executors.newFixedThreadPool(2,r -> new Thread(r,"sms-read"));
    public static final ExecutorService CONTACTS = Executors.newSingleThreadExecutor(r -> new Thread(r,"sms-contacts"));
    public static final ExecutorService DIFF = Executors.newSingleThreadExecutor(r -> new Thread(r,"sms-diff"));
    public static final ExecutorService CHECKPOINT = Executors.newSingleThreadExecutor(r -> new Thread(r,"sms-checkpoint"));
    public static final ExecutorService MAINTENANCE = Executors.newSingleThreadExecutor(r -> new Thread(r,"sms-maintenance"));
    // Broadcast persistence must not queue behind a password KDF or a UI history load.
    public static final ExecutorService INBOUND = Executors.newFixedThreadPool(2, r -> new Thread(r, "sms-inbound"));
}
