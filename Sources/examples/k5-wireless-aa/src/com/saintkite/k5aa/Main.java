package com.saintkite.k5aa;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Looper;
import android.os.Parcel;
import android.os.SystemClock;
import java.io.*;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Properties;

public final class Main {
    public static final String SELF="k5.aa.apalign.guard";
    public static final String STATE="/data/nemesis/k5-aa-apalign";
    public static final String BRIDGE="com.daudio.app.deviceconnections";
    public static final String TARGET="com.daudio.app.phoneprojection.wireless.WPPSettingsActivity";
    static final long TRIAL_MS=8*60*1000L;

    static byte[] read(File f) throws Exception {
        FileInputStream in=new FileInputStream(f);
        try { ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[8192]; int n;
            while((n=in.read(b))!=-1) out.write(b,0,n); return out.toByteArray();
        } finally { in.close(); }
    }
    static void write(File f, byte[] b) throws Exception {
        File tmp=new File(f.getPath()+".tmp");
        FileOutputStream out=new FileOutputStream(tmp);
        try { out.write(b); out.getFD().sync(); } finally { out.close(); }
        tmp.setReadable(false,false); tmp.setWritable(false,false);
        tmp.setReadable(true,true); tmp.setWritable(true,true);
        if (!tmp.renameTo(f)) { if(f.exists() && !f.delete()) throw new IOException("replace "+f.getName());
            if(!tmp.renameTo(f)) throw new IOException("rename "+f.getName()); }
    }
    static String text(File f) throws Exception { return new String(read(f),"UTF-8").trim(); }
    static String sha(byte[] b) throws Exception {
        byte[] h=MessageDigest.getInstance("SHA-256").digest(b); StringBuilder s=new StringBuilder();
        for(byte v:h) s.append(String.format("%02x",v&255)); return s.toString();
    }
    static String boot() throws Exception { return text(new File("/proc/sys/kernel/random/boot_id")); }
    static final class Capture {
        final Process process; final Thread reader;
        Capture(final File dir) throws Exception {
            process=new ProcessBuilder("/system/bin/logcat","-v","time","-s","WifiService:D","WifiStateMachine:I",
                "WifiHW:D","WifiNative:V","WifiMonitor:V","AAProjection:V","PhoneProjection:V","hostapd:D","dnsmasq:D","AndroidRuntime:V","*:S").redirectErrorStream(true).start();
            reader=new Thread(new Runnable(){public void run(){
                try{
                    File target=new File(dir,"radio-log.txt");int initial=(int)Math.min(target.length(),12*1024*1024);
                    FileOutputStream out=new FileOutputStream(target,true);
                    try{BufferedReader in=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"));String line;int total=initial;
                        while((line=in.readLine())!=null && total<12*1024*1024){
                            byte[] b=(DiagnosticText.redact(line)+"\n").getBytes("UTF-8");int size=Math.min(b.length,12*1024*1024-total);out.write(b,0,size);total+=size;
                        }
                        out.getFD().sync();
                    }finally{out.close();process.destroy();}
                }catch(Exception ignored){process.destroy();}
            }});reader.setDaemon(true);reader.start();
        }
        void close(){process.destroy();try{reader.join(2000);}catch(InterruptedException ignored){}}
    }
    public static final class Disk implements Trial.Store {
        final File dir;
        Disk(File d) { dir=d; }
        public String phase() throws Exception { File f=new File(dir,"phase");return f.exists()?text(f):""; }
        public void phase(String p) throws Exception { write(new File(dir,"phase"),(p+"\n").getBytes("UTF-8")); }
        public String decision() throws Exception { File f=new File(dir,"decision");return f.exists()?text(f):""; }
        public void event(String s) { System.out.println(s); try {
            FileOutputStream out=new FileOutputStream(new File(dir,"events.txt"),true);
            try { out.write((System.currentTimeMillis()+" "+s+"\n").getBytes("UTF-8")); out.getFD().sync(); }
            finally { out.close(); }
        } catch(Exception ignored){} }
    }
    static class Device implements Trial.Device, UsbSessionRecovery.Backend {
        final Disk store; final Context context; final WifiManager wifi; final File apk;
        final Properties original=new Properties();
        final UsbSessionRecovery wirelessRecovery=new UsbSessionRecovery(this,false);
        final UsbSessionRecovery finalRecovery=new UsbSessionRecovery(this,true);
        long lastNetworkCapture=-1;
        Device(Disk s,File a) throws Exception {
            store=s;apk=a;
            s.event("DEVICE_CONTEXT_BEGIN");
            Class<?> at=Class.forName("android.app.ActivityThread");
            Object thread=at.getMethod("systemMain").invoke(null);
            context=(Context)at.getMethod("getSystemContext").invoke(thread);
            wifi=(WifiManager)context.getSystemService("wifi");
            if(wifi==null) throw new IOException("Wi-Fi service not ready");
            s.event("DEVICE_WIFI_MANAGER_READY");
            File f=new File(s.dir,"original.properties");
            if(f.exists()) { FileInputStream in=new FileInputStream(f);try{original.load(in);}finally{in.close();} }
        }
        Object call(String name,Class<?>[] types,Object...args) throws Exception {
            Method m=wifi.getClass().getMethod(name,types); return m.invoke(wifi,args);
        }
        String property(String name) throws Exception {
            return (String)Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,name);
        }
        void property(String name,String value) throws Exception {
            Class.forName("android.os.SystemProperties").getMethod("set",String.class,String.class).invoke(null,name,value);
            if(!value.equals(property(name)))throw new IOException("Property restoration/setting failed: "+name);
        }
        int apState() throws Exception { return ((Number)call("getWifiApState",new Class<?>[0])).intValue(); }
        int mode() throws Exception { return ((Number)call("getWifiApMode",new Class<?>[0])).intValue(); }
        WifiConfiguration config() throws Exception { return (WifiConfiguration)call("getWifiApConfiguration",new Class<?>[0]); }
        byte[] parcel(WifiConfiguration c) { Parcel p=Parcel.obtain();try{c.writeToParcel(p,0);return p.marshall();}finally{p.recycle();} }
        WifiConfiguration savedConfig() throws Exception {
            byte[] b=read(new File(store.dir,"original-ap.parcel"));
            if(!sha(b).equals(original.getProperty("configSha")))throw new IOException("AP backup checksum mismatch");
            Parcel p=Parcel.obtain();try{
                p.unmarshall(b,0,b.length);p.setDataPosition(0);
                android.os.Parcelable.Creator<?> creator=(android.os.Parcelable.Creator<?>)WifiConfiguration.class.getField("CREATOR").get(null);
                return (WifiConfiguration)creator.createFromParcel(p);
            }finally{p.recycle();}
        }
        PackageInfo bridge() throws Exception {
            try{return context.getPackageManager().getPackageInfo(BRIDGE,0);}
            catch(PackageManager.NameNotFoundException absent){return null;}
        }
        void snapshotGpmState() throws Exception {
            PackageInfo gpm=context.getPackageManager().getPackageInfo(GpmAccess.PACKAGE,0);
            if((gpm.applicationInfo.flags & ApplicationInfo.FLAG_STOPPED)!=0)throw new IOException("AA package is stopped before trial; no changes made");
            GpmState state=GpmAccess.get().state();store.event("GPM_PREFLIGHT "+state);
            if(state.protectedMode()!=0)throw new IOException("AA is connected or starting; disconnect it before this wireless trial: "+state);
        }
        void snapshot(String bridgeSha) throws Exception { snapshot(bridgeSha,null,null); }
        void snapshot(String bridgeSha,String usbRoot,String mediaId) throws Exception {
            store.event("SNAPSHOT_BEGIN");
            if(new File(store.dir,"original.properties").exists())throw new IOException("Existing backup: do not overwrite");
            String firmware=(String)Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,"ro.build.product2");
            if(!"DL3.KOR.S5W_M.V039.001.260902".equals(firmware))throw new IOException("Wrong firmware");
            if(bridge()!=null)throw new IOException("DeviceConnections package already exists; will not overwrite");
            context.getPackageManager().getActivityInfo(new android.content.ComponentName("com.daudio.app.phoneprojection",TARGET),0);
            WifiConfiguration c=config();if(c==null)throw new IOException("No stock AP configuration; no changes made");
            ApCredentials.checked(c);store.event("AA_CREDENTIAL_FORMAT_VALIDATED");
            int state=apState();if(state!=11 && state!=13)throw new IOException("AP state is not stable: "+state);
            call("getWifiApMode",new Class<?>[0]);
            wifi.getClass().getMethod("setWifiApEnabledForConnectivity",WifiConfiguration.class,boolean.class);
            String bootId=boot();if(bootId.length()<10)throw new IOException("Boot identity unavailable");
            if(usbRoot!=null) {
                File media=new File(usbRoot,"aa/trial-media-id");
                if(!new File(usbRoot).isAbsolute() || mediaId==null || !mediaId.equals(text(media)))throw new IOException("Trial USB identity unavailable");
                snapshotGpmState();
                original.setProperty("usbRoot",usbRoot);original.setProperty("mediaId",mediaId);
                original.setProperty("gpmOriginallyStopped","false");
            }
            store.event("SNAPSHOT_AP_SERIALIZE");
            byte[] bytes=parcel(c);
            original.setProperty("configSha",sha(bytes));original.setProperty("bridgeSha",bridgeSha);
            store.event("SNAPSHOT_AP_HASH_COMPLETE");
            original.setProperty("boot",bootId); original.setProperty("apEnabled",Boolean.toString(state==13));
            original.setProperty("apMode",Integer.toString(mode()));
            original.setProperty("wifiEnabled",Boolean.toString(wifi.isWifiEnabled()));
            original.setProperty("sys.boot_completed",property("sys.boot_completed"));
            store.event("SNAPSHOT_SETTINGS_EXTERNAL_BEGIN");
            for(String key:new String[]{"wifi_ap_on","wifi_on"}) {
                String v=ExternalSettings.get(key);
                original.setProperty(key,v==null?"ABSENT":v);
            }
            store.event("SNAPSHOT_SETTINGS_EXTERNAL_COMPLETE");
            write(new File(store.dir,"original-ap.parcel"),bytes);
            ByteArrayOutputStream out=new ByteArrayOutputStream();original.store(out,"Original K5 AP state; private");
            write(new File(store.dir,"original.properties"),out.toByteArray());
            write(new File(store.dir,"baseline.txt"),("originalApState="+state+"\noriginalApMode="+mode()+
                "\noriginalWifiEnabled="+wifi.isWifiEnabled()+"\noriginalWifiApOn="+original.getProperty("wifi_ap_on")+
                "\noriginalWifiOn="+original.getProperty("wifi_on")+"\noriginalBridgeAbsent=true\noriginalGpmStopped="+original.getProperty("gpmOriginallyStopped","not inspected")+"\n").getBytes("UTF-8"));
            store.event("BACKUP_VERIFIED: AP configuration and persisted states saved privately");
            store.phase("armed");
        }
        int command(String...args) throws Exception {
            final Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
            final ByteArrayOutputStream output=new ByteArrayOutputStream();
            Thread reader=new Thread(new Runnable(){public void run(){
                try{InputStream in=p.getInputStream();try{byte[] b=new byte[4096];int n;
                    while((n=in.read(b))!=-1){if(output.size()<65536)output.write(b,0,n);}
                }finally{in.close();}}catch(IOException ignored){}
            }});reader.setDaemon(true);reader.start();
            long end=now()+45000;int rc;
            for(;;){try{rc=p.exitValue();break;}catch(IllegalThreadStateException running){
                if(now()>=end){p.destroy();throw new IOException("Package command timed out");}pause(200);
            }}
            reader.join(2000);store.event(args[0]+" exit="+rc+" "+new String(output.toByteArray(),"UTF-8").trim());return rc;
        }
        public void installBridge() throws Exception {
            if(bridge()!=null)throw new IOException("Bridge namespace became occupied");
            if(apk==null || !sha(read(apk)).equals(original.getProperty("bridgeSha")))throw new IOException("Bridge APK checksum mismatch");
            String before=property("sys.boot_completed");
            if(!before.equals(original.getProperty("sys.boot_completed")))throw new IOException("Boot state changed before installation");
            // Match NEMESIS's documented HMG install procedure. Always restore this
            // transient property before AP startup; the recovery path restores it too.
            try {
                if("1".equals(before))property("sys.boot_completed","0");
                if(command("/system/bin/pm","install",apk.getPath())!=0 || bridge()==null)throw new IOException("Bridge installation failed");
            } finally { if(!before.equals(property("sys.boot_completed")))property("sys.boot_completed",before); }
            if(!sha(read(new File(bridge().applicationInfo.sourceDir))).equals(original.getProperty("bridgeSha")))throw new IOException("Installed bridge checksum mismatch");
            store.event("SETTINGS_ROUTE_INSTALLED");
        }
        public void startAp() throws Exception {
            WifiConfiguration trial=savedConfig();
            ApCredentials.align(trial);
            store.event("AP_TRIAL_FIELDS_ALIGNED_TO_AA_CONNECTIVITY: private original unchanged");
            Object accepted=call("setWifiApEnabledForConnectivity",new Class<?>[]{WifiConfiguration.class,boolean.class},trial,true);
            if(Boolean.FALSE.equals(accepted))throw new IOException("AP request rejected");
            long end=now()+35000;
            while(now()<end){
                if(apState()==13 && mode()==2) {
                    try {if(apReady()){store.event("AP_ENABLED_MODE_2");store.event("AP_AA_SSID_AND_CREDENTIAL_MATCH_VERIFIED");return;}}
                    catch(Exception unavailable){} // A stale/partially written config is never readiness.
                }
                if(apState()==14)throw new IOException("AP service reported failure");pause(500);
            }
            throw new IOException("AP readiness or AA credential verification failed");
        }
        String hostapdConfiguration() throws Exception { return DiagnosticText.read(new File("/data/misc/wifi/hostapd.conf"),32768); }
        public boolean apReady() throws Exception { return apState()==13 && mode()==2 && ApCredentials.activeMatches(hostapdConfiguration(),config()); }
        public boolean mediaPresent() {
            String root=original.getProperty("usbRoot"),id=original.getProperty("mediaId");
            if(root==null || id==null)return true; // Missing identity must never trigger a reset.
            File marker=new File(root,"aa/trial-media-id");
            if(!marker.exists())return false;
            try{return id.equals(text(marker));}catch(Exception unreadable){return true;}
        }
        public int protectedMode() throws Exception {
            GpmState state=GpmAccess.get().state();store.event("GPM_INSPECT "+state);
            return state.protectedMode();
        }
        void ensureGpmStarted(android.os.IBinder previous,boolean requireNewBinder) throws Exception {
            if(command("/system/bin/am","startservice","-n",GpmAccess.COMPONENT,"--ei","startMode","0")!=0)throw new IOException("AA service start request failed");
            long end=now()+20000;
            while(now()<end) {
                try {
                    GpmAccess.Endpoint current=GpmAccess.get();
                    if((!requireNewBinder || !current.binder.equals(previous)) &&
                       (context.getPackageManager().getPackageInfo(GpmAccess.PACKAGE,0).applicationInfo.flags & ApplicationInfo.FLAG_STOPPED)==0) {
                        current.state().protectedMode();return;
                    }
                } catch(Exception waiting) {}
                pause(300);
            }
            throw new IOException("AA service did not return with the required live Binder");
        }
        public void restartIdleGpm() throws Exception {
            GpmAccess.Endpoint before=GpmAccess.get();
            GpmState state=before.state();store.event("GPM_RESTART_PREFLIGHT "+state);
            if(state.protectedMode()!=0)throw new IOException("Refusing to restart a connected or starting AA session: "+state);
            GpmProcess.Identity process=GpmProcess.find(new File("/proc"));
            write(new File(store.dir,"gpm-touched"),"yes\n".getBytes("UTF-8"));
            store.event("GPM_IDLE_RESTART_BEGIN_AFTER_USB_REMOVAL");
            if(command("/system/bin/am","force-stop",GpmAccess.PACKAGE)!=0)throw new IOException("AA service stop request failed");
            // This ROM marks AA persistent. If it survived force-stop, terminate
            // only the previously verified PID with the same command, UID and start time.
            if(GpmProcess.killIfStillSame(new File("/proc"),process))store.event("PERSISTENT_GPM_OLD_PROCESS_STOPPED_BY_VERIFIED_IDENTITY");
            ensureGpmStarted(before.binder,true);
            store.event("GPM_IDLE_RESTART_VERIFIED_NEW_BINDER");
        }
        void networkSnapshot(String reason) {
            try {
                File target=new File(store.dir,"network-checkpoints.txt");if(target.length()>512*1024)return;
                FileOutputStream out=new FileOutputStream(target,true);
                try {
                    out.write(("TIME="+System.currentTimeMillis()+" reason="+reason+"\n").getBytes("UTF-8"));
                    for(String path:new String[]{"/proc/net/tcp","/proc/net/tcp6","/proc/net/arp","/proc/net/route","/data/misc/dnsmasq/dnsmasq.leases","/data/misc/dhcp/dnsmasq.leases","/data/misc/wifi/dnsmasq.leases"}) {
                        File source=new File(path);if(!source.isFile())continue;
                        out.write((path+"\n").getBytes("UTF-8"));out.write(read(source));out.write('\n');
                    }
                    out.getFD().sync();
                } finally {out.close();}
            } catch(Exception e){store.event("NETWORK_SNAPSHOT_UNAVAILABLE: "+e);}
        }
        public boolean prepareWireless() throws Exception {
            boolean ready=wirelessRecovery.step();
            if(ready && (lastNetworkCapture<0 || now()-lastNetworkCapture>=10000)) {
                networkSnapshot("wireless trial after USB removal");lastNetworkCapture=now();
            }
            return ready;
        }
        void finishCommitted() throws Exception {
            if(!new File(store.dir,"export-complete").exists())return;
            if(!finalRecovery.step())return;
            store.event("POST_EXPORT_USB_REMOVAL_RECOVERY_COMPLETE");
            // NEMESIS stops only this registered worker PID. Its independent
            // uninstall child completes cleanup; persistent backups remain here.
            Process cleanup=new ProcessBuilder("/system/bin/sh","-c",
                "/system/xbin/nemesis uninstall k5.aa.apalign.guard > /data/nemesis/k5-aa-apalign/post-usb-cleanup.txt 2>&1").start();
            if(cleanup.waitFor()==0)System.exit(0);
            throw new IOException("Deferred guard cleanup failed");
        }
        void waitAp(boolean enabled) throws Exception {
            long end=now()+30000;while(now()<end){if(apState()==(enabled?13:11))return;pause(500);}
            throw new IOException("AP restore did not reach original enabled state");
        }
        public void restore() throws Exception {
            networkSnapshot("before restoration");
            WifiConfiguration c=savedConfig();
            String bootCompleted=original.getProperty("sys.boot_completed");
            if(bootCompleted!=null && !bootCompleted.equals(property("sys.boot_completed")))property("sys.boot_completed",bootCompleted);
            call("setWifiApEnabledForConnectivity",new Class<?>[]{WifiConfiguration.class,boolean.class},null,false);
            waitAp(false);
            call("setWifiApConfiguration",new Class<?>[]{WifiConfiguration.class},c);
            if(Boolean.parseBoolean(original.getProperty("apEnabled"))) {
                String method="2".equals(original.getProperty("apMode"))?"setWifiApEnabledForConnectivity":"setWifiApEnabled";
                call(method,new Class<?>[]{WifiConfiguration.class,boolean.class},savedConfig(),true);waitAp(true);
            }
            boolean wasWifi=Boolean.parseBoolean(original.getProperty("wifiEnabled"));
            if(wifi.isWifiEnabled()!=wasWifi)wifi.setWifiEnabled(wasWifi);
            long end=now()+15000;
            while(now()<end && wifi.isWifiEnabled()!=wasWifi)pause(300);
            if(wifi.isWifiEnabled()!=wasWifi)throw new IOException("Wi-Fi client restore failed");
            for(String key:new String[]{"wifi_ap_on","wifi_on"}) {
                String value=original.getProperty(key);
                ExternalSettings.put(key,"ABSENT".equals(value)?null:value);
                String actual=ExternalSettings.get(key);
                if("ABSENT".equals(value)?actual!=null:!value.equals(actual))throw new IOException("Setting verification failed: "+key);
            }
            // Confirm the exact OEM Parcelable round-trip, without logging SSID or passwords.
            if(!Arrays.equals(parcel(config()),read(new File(store.dir,"original-ap.parcel"))))throw new IOException("AP configuration restore verification failed");
            if(new File(store.dir,"gpm-touched").exists()) {
                ensureGpmStarted(null,false);
                store.event("ORIGINAL_AA_PACKAGE_RUNNING_STATE_RESTORED");
            }
            PackageInfo pi=bridge();
            if(pi!=null) {
                if(!sha(read(new File(pi.applicationInfo.sourceDir))).equals(original.getProperty("bridgeSha")))throw new IOException("Unknown package in bridge namespace: refusing uninstall");
                if(command("/system/bin/pm","uninstall",BRIDGE)!=0 || bridge()!=null)throw new IOException("Bridge removal failed");
            }
            store.event("ORIGINAL_AP_SETTINGS_AND_ROUTE_RESTORED");
            verifyRestored();
        }
        void verifyRestored() throws Exception {
            savedConfig(); // Validate the private backup checksum before comparisons.
            if(!Arrays.equals(parcel(config()),read(new File(store.dir,"original-ap.parcel"))))throw new IOException("AP backup differs from current configuration");
            if((apState()==13)!=Boolean.parseBoolean(original.getProperty("apEnabled")) || (apState()!=11 && apState()!=13))throw new IOException("AP enabled state differs from backup");
            if(Boolean.parseBoolean(original.getProperty("apEnabled")) && mode()!=Integer.parseInt(original.getProperty("apMode")))throw new IOException("AP mode differs from backup");
            if(wifi.isWifiEnabled()!=Boolean.parseBoolean(original.getProperty("wifiEnabled")))throw new IOException("Wi-Fi client differs from backup");
            for(String key:new String[]{"wifi_ap_on","wifi_on"}) {
                String wanted=original.getProperty(key),actual=ExternalSettings.get(key);
                if(wanted==null || ("ABSENT".equals(wanted)?actual!=null:!wanted.equals(actual)))throw new IOException("Saved setting differs: "+key);
            }
            if(!original.getProperty("sys.boot_completed").equals(property("sys.boot_completed")))throw new IOException("Boot property differs from backup");
            if(bridge()!=null)throw new IOException("Temporary bridge is still installed");
            if(new File(store.dir,"gpm-touched").exists()) {
                if((context.getPackageManager().getPackageInfo(GpmAccess.PACKAGE,0).applicationInfo.flags & ApplicationInfo.FLAG_STOPPED)!=0)throw new IOException("AA package remains stopped");
                GpmAccess.get().state();
            }
            store.event("INDEPENDENT_RESTORE_VERIFIED");
        }
        public void pause(long n) throws Exception { Thread.sleep(n); }
        public long now(){return SystemClock.elapsedRealtime();}
    }
    public static void main(String[] args) {
        int exit=0;
        try {
            System.err.println("K5AA_ENTRY mode="+(args.length==0?"missing":args[0]));
            entry(args);
        } catch(Throwable failure) {
            System.err.println("K5AA_FAILED");
            failure.printStackTrace(System.err);
            exit=1;
        } finally {
            System.out.flush();System.err.flush();
            // A root command has no Activity lifecycle. Binder/framework threads
            // survive a normal main() return, so every completed command must exit.
            System.exit(exit);
        }
    }
    private static void entry(String[] args) throws Exception {
        if(args.length<2)throw new IllegalArgumentException("mode state [bridge-apk-or-sha]");
        if(Looper.myLooper()==null)Looper.prepareMainLooper();
        File dir=new File(args[1]);dir.mkdirs();
        dir.setReadable(false,false);dir.setWritable(false,false);dir.setExecutable(false,false);
        dir.setReadable(true,true);dir.setWritable(true,true);dir.setExecutable(true,true);
        Disk s=new Disk(dir);
        if("ap-info".equals(args[0])) {
            System.out.println(ApComparison.publicInfo(DiagnosticText.read(new File("/data/misc/wifi/hostapd.conf"),32768)));return;
        }
        if("verify-restored".equals(args[0])) {
            if(!"rolled_back".equals(s.phase()))throw new IOException("Previous trial is not rolled back");
            new Device(s,null).verifyRestored();return;
        }
        if("preflight".equals(args[0])) {
            Device d=new Device(s,null);
            // Reproduce the old read-only acquisition path inside a catch so the
            // exact OEM exception is captured instead of RuntimeInit killing us.
            s.event("LEGACY_SETTINGS_READ_BEGIN");
            try {
                android.provider.Settings.Global.getString(d.context.getContentResolver(),"wifi_ap_on");
                s.event("LEGACY_SETTINGS_READ_SUCCEEDED");
            } catch(Throwable legacy) {
                s.event("LEGACY_SETTINGS_READ_FAILED");legacy.printStackTrace(System.err);
            }
            d.snapshot("read-only-preflight");
            if(!Arrays.equals(d.parcel(d.savedConfig()),read(new File(dir,"original-ap.parcel"))))
                throw new IOException("Snapshot Parcelable round-trip mismatch");
            if(!"armed".equals(s.phase()))throw new IOException("Snapshot did not complete");
            s.event("PREFLIGHT_READ_ONLY_COMPLETE: backup verified; no AP setter or app install invoked");
            return;
        }
        if("snapshot".equals(args[0])){
            if(args.length<5)throw new IllegalArgumentException("snapshot requires bridge SHA, USB root and media ID");
            new Device(s,null).snapshot(args[2],args[3],args[4]);return;
        }
        if("status".equals(args[0])){
            Device d=new Device(s,null);
            System.out.println("phase="+s.phase()+"\napState="+d.apState()+"\napMode="+d.mode()+"\nwifiClient="+d.wifi.isWifiEnabled()+
                "\nwifi_ap_on="+ExternalSettings.get("wifi_ap_on")+"\nbridgePresent="+(d.bridge()!=null));return;
        }
        if("restore".equals(args[0])) {
            if(!"rolled_back".equals(s.phase()))Trial.rollback(s,new Device(s,null),"manual restoration");return;
        }
        if("cleanup".equals(args[0])) {
            if(!Trial.finished(s.phase()))Trial.rollback(s,new Device(s,null),"guard removed while pending");return;
        }
        if(!"run".equals(args[0]))throw new IllegalArgumentException("Unknown mode");
        Device current=null;
        for(;;) {
            if("rolled_back".equals(s.phase())){Thread.sleep(60000);continue;}
            try {
                if("committed".equals(s.phase())) {
                    if(current==null)current=new Device(s,null);
                    try{current.finishCommitted();}
                    catch(Exception failure){Trial.rollback(s,current,"post-export AA recovery failed: "+failure);}
                    Thread.sleep(1000);continue;
                }
                Device d=new Device(s,new File(args[2]));
                current=d;
                Capture capture=null;NetworkDiagnostics diagnostics=null;
                try { capture=new Capture(dir); } catch(Exception e){s.event("Log capture unavailable: "+e);}
                try { diagnostics=new NetworkDiagnostics(d);diagnostics.start();s.event("READ_ONLY_NETWORK_DIAGNOSTICS_ARMED"); }
                catch(Throwable e){s.event("Network diagnostics unavailable: "+e.getClass().getSimpleName());}
                try{Trial.run(s,d,boot().equals(d.original.getProperty("boot")),TRIAL_MS);}
                finally{if(diagnostics!=null)diagnostics.close();if(capture!=null)capture.close();}
            }catch(Exception e){s.event("RECOVERY_RETRY: "+e);Thread.sleep(5000);}
        }
    }
}
