package com.saintkite.k5aa;
import java.io.*;
import java.util.*;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.os.Parcel;

public final class ApCredentialsTest {
    static void check(boolean b){if(!b)throw new AssertionError();}
    static final class Sim extends Main.Device {
        long clock;String failure="",choice="keep";boolean sawAligned,driftAfterStart;
        Sim(Main.Disk disk)throws Exception{super(disk,null);}
        public void installBridge(){} // Package install/removal has separate fault-injection coverage.
        public long now(){return clock;}
        public void pause(long ms)throws Exception {
            clock+=ms;
            if("testing".equals(store.phase()) && choice.length()>0)Main.write(new File(store.dir,"decision"),(choice+"\n").getBytes("UTF-8"));
        }
        public boolean prepareWireless(){if(driftAfterStart)failure="credential";return true;}
        String hostapdConfiguration()throws Exception {
            WifiConfiguration c=config();
            sawAligned=c.SSID.equals(c.ConnectivitySSID) && c.preSharedKey.equals(c.ConnectivityPSK);
            String name=failure.equals("ssid")?"other":c.SSID;
            String pass=failure.equals("credential")?"wrong-test-pass":c.preSharedKey;
            String key=ApComparison.derivedKey(pass,name.getBytes("UTF-8"));
            return "interface=wlan0\nssid="+name+"\nwpa="+(failure.equals("security")?"0":"2")+"\nrsn_pairwise=CCMP\n"+
                (failure.equals("unknown")?"":"wpa_psk="+key+"\n");
        }
    }
    static Sim seed(File root,String scenario)throws Exception {
        File dir=new File(root,scenario);dir.mkdirs();
        WifiConfiguration c=new WifiConfiguration();
        c.SSID="ordinary-hotspot";c.preSharedKey="ordinary-test-pass";c.ConnectivitySSID="AA-trial";c.ConnectivityPSK="AA-only-test-pass";
        if(scenario.equals("invalid"))c.ConnectivityPSK="short";
        Context.wifi.config=c.copy();Context.wifi.state=11;Context.wifi.client=false;Context.wifi.ignoreRestore=false;
        android.provider.Settings.Global.values.clear();android.provider.Settings.Global.values.put("wifi_ap_on","0");android.provider.Settings.Global.values.put("wifi_on","0");
        android.os.SystemProperties.set("sys.boot_completed","1");
        Parcel p=Parcel.obtain();c.writeToParcel(p,0);byte[] backup=p.marshall();
        Main.write(new File(dir,"original-ap.parcel"),backup);
        Properties props=new Properties();props.setProperty("configSha",Main.sha(backup));props.setProperty("bridgeSha","test");
        props.setProperty("apEnabled","false");props.setProperty("apMode","0");props.setProperty("wifiEnabled","false");
        props.setProperty("wifi_ap_on","0");props.setProperty("wifi_on","0");props.setProperty("sys.boot_completed","1");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();props.store(bytes,"");Main.write(new File(dir,"original.properties"),bytes.toByteArray());
        Main.Disk disk=new Main.Disk(dir);disk.phase("armed");return new Sim(disk);
    }
    static void restored(Sim d)throws Exception {
        d.verifyRestored();
        check(Arrays.equals(d.parcel(d.config()),Main.read(new File(d.store.dir,"original-ap.parcel"))));
        check(Context.wifi.config.SSID.equals("ordinary-hotspot") && Context.wifi.config.preSharedKey.equals("ordinary-test-pass"));
        check(Context.wifi.state==11 && d.mode()==0 && d.bridge()==null);
    }
    public static void main(String[] args)throws Exception {
        File root=new File(args[0]);Sim d=seed(root,"keep");
        byte[] baseline=Main.read(new File(d.store.dir,"original-ap.parcel"));
        Trial.run(d.store,d,true,3000);
        check(d.store.phase().equals("committed") && d.sawAligned && d.apReady());
        check(Arrays.equals(baseline,Main.read(new File(d.store.dir,"original-ap.parcel"))));
        check(d.config().ConnectivitySSID.equals("AA-trial") && d.config().ConnectivityPSK.equals("AA-only-test-pass"));
        Trial.rollback(d.store,d,"test explicit restore after keep");restored(d);
        System.out.println("PASS real AP adapter aligns only copied hotspot fields, verifies derived on-disk credentials, keeps on confirmation and restores exact baseline");
        for(String kind:new String[]{"ssid","credential","unknown","security","invalid"}) {
            d=seed(root,kind);d.failure=kind;
            Trial.run(d.store,d,true,3000);
            check(d.store.phase().equals("rolled_back"));restored(d);
            check(!Main.text(new File(d.store.dir,"events.txt")).contains("AP_AA_SSID_AND_CREDENTIAL_MATCH_VERIFIED"));
            System.out.println("PASS real AP readiness failure restores without reporting ready: "+kind);
        }
        d=seed(root,"timeout");d.choice="";Trial.run(d.store,d,true,3000);restored(d);
        check(d.store.phase().equals("rolled_back") && d.sawAligned);
        System.out.println("PASS aligned credentials are rolled back when no real-AA success is confirmed");
        d=seed(root,"rollback");d.choice="rollback";Trial.run(d.store,d,true,3000);restored(d);
        check(d.store.phase().equals("rolled_back") && d.sawAligned);
        System.out.println("PASS explicit failed AA result restores all original AP credential fields");
        d=seed(root,"drift");d.driftAfterStart=true;Trial.run(d.store,d,true,3000);restored(d);
        check(d.store.phase().equals("rolled_back"));
        System.out.println("PASS credential drift after initial readiness rejects keep and restores even with a success selection");
    }
}
