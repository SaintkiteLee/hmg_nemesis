package com.saintkite.k5aa;

/** Transaction policy, independent of Android for fault-injection tests. */
public final class Trial {
    public interface Store {
        String phase() throws Exception;
        void phase(String value) throws Exception;
        String decision() throws Exception;
        void event(String text);
    }
    public interface Device {
        void installBridge() throws Exception;
        void startAp() throws Exception;
        boolean apReady() throws Exception;
        boolean prepareWireless() throws Exception;
        void restore() throws Exception;
        void pause(long millis) throws Exception;
        long now();
    }
    public static boolean finished(String p) {
        return "committed".equals(p) || "rolled_back".equals(p);
    }
    public static void rollback(Store s, Device d, String reason) throws Exception {
        s.event("ROLLBACK: " + reason);
        s.phase("restoring");
        d.restore();
        s.phase("rolled_back");
        s.event("RESTORE_VERIFIED");
    }
    public static void run(Store s, Device d, boolean sameBoot, long timeout) throws Exception {
        String p=s.phase();
        if (finished(p)) return;
        if (!"armed".equals(p) || !sameBoot) {
            rollback(s,d,"interrupted transaction / changed boot");
            return;
        }
        if ("rollback".equals(s.decision())) { rollback(s,d,"restore requested before apply"); return; }
        try {
            s.phase("applying");
            d.installBridge();
            if ("rollback".equals(s.decision()) || !"applying".equals(s.phase())) { rollback(s,d,"apply canceled");return; }
            d.startAp();
            if ("rollback".equals(s.decision()) || !"applying".equals(s.phase())) { rollback(s,d,"AP apply canceled");return; }
            if (!d.apReady()) throw new IllegalStateException("AP not enabled in connectivity mode");
            s.phase("testing");
            s.event("AP_READY: remove USB, test wireless AA, reinsert USB and choose keep or restore");
            long deadline=d.now()+timeout;
            while(d.now()<deadline) {
                String decision=s.decision();
                if ("rollback".equals(decision)) {
                    rollback(s,d,"user reports AA failure"); return;
                }
                boolean prepared=d.prepareWireless();
                if ("keep".equals(decision)) {
                    if(!prepared)throw new IllegalStateException("Wireless preparation has not completed after USB removal");
                    if (!d.apReady()) throw new IllegalStateException("AP no longer ready at confirmation");
                    s.phase("committed");
                    s.event("KEPT: user confirmed real wireless AA connection"); return;
                }
                d.pause(1000);
            }
            rollback(s,d,"no successful-AA confirmation within trial window");
        } catch (Exception failure) {
            if (!"restoring".equals(s.phase())) rollback(s,d,failure.toString());
            else throw failure; // Recovery service retries, never reports a false restore.
        }
    }
}
