package com.saintkite.k5aa;

/** Installation transaction; no connection trial, user survey or network capture. */
public final class Installation {
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
    public static boolean finished(String phase) {
        return "installed".equals(phase) || "rolled_back".equals(phase);
    }
    public static void rollback(Store s, Device d, String reason) throws Exception {
        s.event("RESTORE_BEGIN: "+reason);
        s.phase("restoring");
        d.restore();
        s.phase("rolled_back");
        s.event("RESTORE_VERIFIED");
    }
    public static void run(Store s, Device d, boolean sameBoot, long usbTimeout) throws Exception {
        if(finished(s.phase()))return;
        if(!"armed".equals(s.phase()) || !sameBoot) {
            rollback(s,d,"interrupted installation");return;
        }
        try {
            if("rollback".equals(s.decision())){rollback(s,d,"canceled");return;}
            s.phase("applying");d.installBridge();
            if("rollback".equals(s.decision())){rollback(s,d,"canceled");return;}
            d.startAp();
            if(!d.apReady())throw new IllegalStateException("AP verification failed");
            s.phase("waiting_usb");s.event("AP_READY_REMOVE_USB");
            long deadline=d.now()+usbTimeout;
            while(d.now()<deadline) {
                if("rollback".equals(s.decision())){rollback(s,d,"canceled");return;}
                if(d.prepareWireless()) {
                    if(!d.apReady())throw new IllegalStateException("AP configuration changed");
                    s.phase("installed");
                    s.event("INSTALLED: AP verified and idle AA prepared; connect from phone");return;
                }
                d.pause(500);
            }
            rollback(s,d,"installation USB not removed in time");
        } catch(Exception failure) {
            if("restoring".equals(s.phase()))throw failure;
            rollback(s,d,failure.getClass().getSimpleName());
        }
    }
}
