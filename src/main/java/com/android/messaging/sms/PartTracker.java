package com.android.messaging.sms;

/** Each part is pending (0), successful (1), or failed (2). Failure is sticky. */
public final class PartTracker {
    private PartTracker() { }
    public static void record(int[] parts, int index, boolean success) {
        if (parts == null || index < 0 || index >= parts.length)
            throw new IllegalArgumentException("Invalid SMS part");
        if (!success) parts[index] = 2;
        else if (parts[index] == 0) parts[index] = 1;
    }
    public static boolean complete(int[] parts) {
        if (parts == null || parts.length == 0) return false;
        for (int part : parts) if (part != 1) return false;
        return true;
    }
    public static boolean failed(int[] parts) {
        if (parts != null) for (int part : parts) if (part == 2) return true;
        return false;
    }
}
