package com.saintkite.k5aa;

/** Reset the idle AA process only after the installation USB is absent and stable. */
public final class UsbSessionRecovery {
    public interface Backend {
        boolean mediaPresent();
        int protectedMode() throws Exception;
        void restartIdleGpm() throws Exception;
        long now();
    }
    private final Backend backend;
    private final boolean preserveWired;
    private long absentSince=-1;
    private boolean prepared;
    public UsbSessionRecovery(Backend b,boolean preserveWired){backend=b;this.preserveWired=preserveWired;}
    public boolean step() throws Exception {
        if(prepared)return true;
        if(backend.mediaPresent()){absentSince=-1;return false;}
        long time=backend.now();
        if(absentSince<0){absentSince=time;return false;}
        if(time-absentSince<2000)return false;
        int mode=backend.protectedMode();
        if(mode==0)backend.restartIdleGpm();
        else if(mode!=2 && !(preserveWired && mode==1))throw new IllegalStateException("An active wired/unknown AA session prevents a wireless reset: "+mode);
        prepared=true;return true;
    }
}
