package com.android.messaging.util;

import android.os.Handler;
import android.os.Looper;
import com.android.messaging.security.Vault;
import com.android.messaging.sms.ProviderMigration;

/** Main-thread controller: one read in flight and at most one pending refresh per screen. */
public final class MessageLoader<T> {
    public interface Read<T> { T run() throws Exception; }
    public interface Result<T> { void accept(T value, Exception error); }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Read<T> read;
    private final Result<T> result;
    private boolean active, running, dirty;
    private int session;
    private final Runnable start = this::start;

    public MessageLoader(Read<T> read, Result<T> result) {
        this.read = read;
        this.result = result;
    }
    public void invalidate() { session++; request(); }
    public void resume() { active = true; request(); }
    public void pause() { active = false; dirty = false; session++; main.removeCallbacks(start); }
    public void request() {
        if (!active) return;
        dirty = true;
        if (!running) { main.removeCallbacks(start); main.postDelayed(start, 150); }
    }
    private void start() {
        if (!active || running || !dirty) return;
        dirty = false;
        // The move sends a final invalidation, including on failure/cancellation.
        if (ProviderMigration.isMoving()) return;
        running = true;
        final int expected = session;
        new Thread(() -> {
            T value = null;
            Exception failure = null;
            try { if (Vault.unlocked()) value = read.run(); }
            catch (Exception e) { failure = e; }
            final T data = value;
            final Exception error = failure;
            main.post(() -> {
                running = false;
                if (active && session == expected && !ProviderMigration.isMoving()) result.accept(data, error);
                if (active && dirty) main.postDelayed(start, 150);
            });
        }, "message-loader").start();
    }
}
