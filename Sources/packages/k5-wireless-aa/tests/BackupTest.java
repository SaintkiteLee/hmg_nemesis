package com.saintkite.k5aa;
import java.io.*;import java.util.*;
import android.content.Context;import android.net.wifi.WifiConfiguration;
public final class BackupTest {
    static class Sim extends Main.Device {
        Sim(Main.Disk s)throws Exception{super(s,null);}
        String bootIdentity(){return "synthetic-boot-identity";}
    }
    static void check(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        File dir=new File(args[0]);dir.mkdirs();Main.Disk s=new Main.Disk(dir);
        Context.wifi.config=new WifiConfiguration();Context.wifi.state=11;Context.wifi.client=false;
        android.os.SystemProperties.set("ro.build.product2","DL3.KOR.S5W_M.V039.001.260902");
        android.os.SystemProperties.set("sys.boot_completed","1");
        android.provider.Settings.Global.values.clear();
        android.os.ServiceManager.current=new android.os.ServiceManager.GpmBinder();
        android.content.pm.PackageManager.gpmFlags=0;
        File usb=new File(dir,"usb/packages");usb.mkdirs();Main.write(new File(usb,"media-id"),"synthetic-media".getBytes("UTF-8"));
        Sim d=new Sim(s);d.snapshot("synthetic-bridge",usb.getParentFile().getAbsolutePath(),"synthetic-media");
        byte[] baseline=Main.read(new File(dir,"original-ap.parcel"));byte[] properties=Main.read(new File(dir,"original.properties"));
        check(s.phase().equals("armed"));check(d.config().SSID.equals("original"));
        try{d.snapshot("replacement",usb.getParentFile().getAbsolutePath(),"synthetic-media");throw new AssertionError();}catch(IOException expected){}
        check(Arrays.equals(baseline,Main.read(new File(dir,"original-ap.parcel"))));
        check(Arrays.equals(properties,Main.read(new File(dir,"original.properties"))));
        System.out.println("PASS fresh snapshot and pending-install refusal preserve the private original");
        d.restore();s.phase("rolled_back");d=new Sim(s);
        d.snapshot("replacement-bridge",usb.getParentFile().getAbsolutePath(),"synthetic-media");
        check(s.phase().equals("armed"));check(Arrays.equals(baseline,Main.read(new File(dir,"original-ap.parcel"))));
        check(d.original.getProperty("bridgeSha").equals("replacement-bridge"));
        check(d.original.getProperty("configSha").equals(Main.sha(baseline)));
        System.out.println("PASS retry after verified rollback retains the exact original baseline");
        d.restore();s.phase("rolled_back");Main.write(new File(dir,"original-ap.parcel"),new byte[]{1,2});
        d=new Sim(s);properties=Main.read(new File(dir,"original.properties"));
        try{d.snapshot("replacement",usb.getParentFile().getAbsolutePath(),"synthetic-media");throw new AssertionError();}catch(IOException expected){}
        check(Arrays.equals(properties,Main.read(new File(dir,"original.properties"))));check(Context.wifi.state==11);
        System.out.println("PASS corrupt baseline rejects retry before mutation or metadata overwrite");
    }
}
