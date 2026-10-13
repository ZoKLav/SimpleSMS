package com.android.messaging.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Workers {
    private Workers() { }
    public static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> new Thread(r, "sms-storage"));
    // Broadcast persistence must not queue behind a password KDF or a UI history load.
    public static final ExecutorService INBOUND = Executors.newFixedThreadPool(2, r -> new Thread(r, "sms-inbound"));
}
