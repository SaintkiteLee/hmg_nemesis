package com.nemesis.manager;

/** Counts releases of the chosen button. Three inside GAP_MS open Manager.
 *  A longer gap starts over. Two broadcasts for one physical press do not. */
final class SetupPress {
    static final long GAP_MS = 1500;
    /** ModeManager and PhoneWindowManager can both announce one press. */
    static final long DEBOUNCE_MS = 200;
    static final int NEED = 3;
    private int count;
    private long lastMs;

    void restore(int count, long lastMs) {
        this.count = count;
        this.lastMs = lastMs;
    }

    int count() {
        return count;
    }

    long lastMs() {
        return lastMs;
    }

    boolean note(long now) {
        if (lastMs != 0 && now >= lastMs && now - lastMs < DEBOUNCE_MS) return false;
        if (now - lastMs > GAP_MS) count = 0;
        lastMs = now;
        count++;
        if (count >= NEED) {
            count = 0;
            return true;
        }
        return false;
    }
}
