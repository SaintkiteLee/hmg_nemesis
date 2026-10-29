package com.saintkite.k5aa;

import java.io.*;
import java.util.*;
import android.net.LocalSocket;
import android.content.Context;

public final class NetworkDiagnosticsTest {
    static void check(boolean condition){if(!condition)throw new AssertionError();}
    static void fixture(File root,String path,String value)throws Exception {
        File file=new File(root,path);file.getParentFile().mkdirs();Main.write(file,value.getBytes("UTF-8"));
    }
    public static final class Client {
        public String getHWAddr(){return "12:34:56:78:90:ab";}
        public String getIpAddr(){return "192.168.0.2";}
        public boolean isReachable(){return true;}
    }
    public static void main(String[] args)throws Exception {
        File root=new File(args[0]);root.mkdirs();File state=new File(root,"state");state.mkdirs();
        fixture(root,"data/misc/wifi/hostapd.conf","interface=wlan0\nctrl_interface=/data/misc/wifi/hostapd\nssid=private-network\nwpa_passphrase=do-not-export-this\nwpa_psk=also-do-not-export\nchannel=36\nwpa_key_mgmt=WPA-PSK\n");
        fixture(root,"data/misc/dnsmasq/dnsmasq.leases","1800000000 12:34:56:78:90:ab 192.168.0.2 phone *\n");
        fixture(root,"proc/net/tcp","tcp-listener-40515\n");fixture(root,"proc/net/tcp6","ipv6-established-evidence\n");
        fixture(root,"proc/net/arp","192.168.0.2 0x1 0x2 12:34:56:78:90:ab * wlan0\n");
        android.os.ServiceManager.current=new android.os.ServiceManager.GpmBinder();
        Context.wifi.clients=Arrays.<Object>asList(new Client());
        Main.Device device=new Main.Device(new Main.Disk(state),null);
        NetworkDiagnostics diagnostics=new NetworkDiagnostics(device,root);diagnostics.captureOnce("fixture",true);
        String snapshot=Main.text(new File(state,"network-snapshots.txt"));
        check(snapshot.contains("/data/misc/dnsmasq/dnsmasq.leases"));check(snapshot.contains("1800000000 12:34:56:78:90:ab 192.168.0.2"));
        check(snapshot.contains("ipv6-established-evidence"));check(snapshot.contains("OEM_CLIENTS count=1"));check(snapshot.contains("getIpAddr=192.168.0.2"));
        check(snapshot.contains("channel=36"));check(!snapshot.contains("do-not-export"));check(!snapshot.contains("private-network"));
        System.out.println("PASS collector reads real OEM lease path, ARP, IPv6 TCP and typed client fields without exporting AP credentials");
        check(DiagnosticText.redact("wpa_passphrase=secret").contains("omitted"));
        check(DiagnosticText.redact("preSharedKey: secret").contains("omitted"));
        check(DiagnosticText.redact("ConnectivityPSK: secret").contains("omitted"));
        check(DiagnosticText.redact("wifiPassword=secret").contains("omitted"));
        check(DiagnosticText.redact("wpa_key_mgmt=WPA-PSK").contains("WPA-PSK"));
        check(DiagnosticText.ctrlServer(DiagnosticText.apFields("ctrl_interface=/tmp/outside\ninterface=wlan0"))==null);
        check(DiagnosticText.ctrlServer(DiagnosticText.apFields("ctrl_interface=/data/misc/../bad\ninterface=wlan0"))==null);
        check(DiagnosticText.ctrlServer(DiagnosticText.apFields("ctrl_interface=DIR=/data/misc/wifi/hostapd GROUP=wifi\ninterface=wlan0")).equals("/data/misc/wifi/hostapd/wlan0"));
        System.out.println("PASS credentials are filtered and hostapd endpoints remain within the expected private data directory");
        LocalSocket.commands.clear();HostapdControl query=new HostapdControl(state,"/data/misc/wifi/hostapd/wlan0");
        check(query.request("PING").equals("PONG"));
        int before=LocalSocket.commands.size();
        for(String command:new String[]{"SET wpa 0","GET_CONFIG","DEAUTHENTICATE 12:34:56:78:90:ab","STA-NEXT ../bad"}) {
            try{query.request(command);throw new AssertionError();}catch(IOException expected){}
        }
        check(LocalSocket.commands.size()==before);query.close();
        System.out.println("PASS hostapd control accepts only read-only queries and transient event subscription commands");
        LocalSocket.commands.clear();int apState=Context.wifi.state,mode=Context.wifi.config.apMode;
        diagnostics.start();long deadline=System.currentTimeMillis()+4000;
        File events=new File(state,"ap-auth-events.txt");
        while(System.currentTimeMillis()<deadline && (!events.exists() || !Main.text(events).contains("[AUTHORIZED]")))Thread.sleep(30);
        diagnostics.close();String trace=Main.text(events);
        check(trace.contains("HOSTAPD_EVENT_MONITOR_ATTACHED"));check(trace.contains("[AUTHORIZED]"));
        check(LocalSocket.commands.contains("ATTACH") && LocalSocket.commands.contains("DETACH"));check(LocalSocket.open==0);
        check(Context.wifi.state==apState && Context.wifi.config.apMode==mode);
        System.out.println("PASS abstract clients allow wifi-UID replies, query/event sockets stay separate, and close detaches without changing AP state");
        File unavailable=new File(root,"control-unavailable");unavailable.mkdirs();LocalSocket.failConnect=true;
        NetworkDiagnostics fallback=new NetworkDiagnostics(new Main.Device(new Main.Disk(unavailable),null),root);fallback.start();
        deadline=System.currentTimeMillis()+4000;File failedEvents=new File(unavailable,"ap-auth-events.txt");
        while(System.currentTimeMillis()<deadline && (!failedEvents.exists() || !Main.text(failedEvents).contains("HOSTAPD_CONTROL_UNAVAILABLE")))Thread.sleep(30);
        fallback.close();LocalSocket.failConnect=false;
        check(Main.text(failedEvents).contains("HOSTAPD_CONTROL_UNAVAILABLE"));check(LocalSocket.open==0);
        check(Main.text(new File(unavailable,"network-snapshots.txt")).contains("ipv6-established-evidence"));
        check(Context.wifi.state==apState && Context.wifi.config.apMode==mode);
        System.out.println("PASS unavailable authentication control records the gap, keeps other evidence and closes sockets without changing AP state");
        File eofState=new File(root,"station-eof");eofState.mkdirs();LocalSocket.eofStations=true;LocalSocket.commands.clear();
        NetworkDiagnostics eof=new NetworkDiagnostics(new Main.Device(new Main.Disk(eofState),null),root);
        java.lang.reflect.Method open=NetworkDiagnostics.class.getDeclaredMethod("openControl");open.setAccessible(true);
        java.lang.reflect.Method station=NetworkDiagnostics.class.getDeclaredMethod("stationSnapshot");station.setAccessible(true);
        java.lang.reflect.Method drain=NetworkDiagnostics.class.getDeclaredMethod("drainEvents");drain.setAccessible(true);
        open.invoke(eof);station.invoke(eof);drain.invoke(eof);
        String eofTrace=Main.text(new File(eofState,"ap-auth-events.txt"));
        check(eofTrace.contains("HOSTAPD_QUERY_LOST")&&eofTrace.contains("AP-STA-CONNECTED"));
        check(!LocalSocket.commands.contains("DETACH"));
        java.lang.reflect.Field retry=NetworkDiagnostics.class.getDeclaredField("nextControlAttempt");retry.setAccessible(true);retry.setLong(eof,0);
        open.invoke(eof);check(Collections.frequency(LocalSocket.commands,"ATTACH")==1);
        eof.close();LocalSocket.eofStations=false;check(LocalSocket.open==0);check(LocalSocket.commands.contains("DETACH"));
        System.out.println("PASS vehicle station-query EOF preserves authentication events and query reconnect never replaces the live monitor");
        File idleState=new File(root,"generic-timeout");idleState.mkdirs();LocalSocket.commands.clear();LocalSocket.emptyReads=0;
        NetworkDiagnostics idle=new NetworkDiagnostics(new Main.Device(new Main.Disk(idleState),null),root);open.invoke(idle);
        for(int i=0;i<20;i++)drain.invoke(idle);
        Thread.sleep(450);drain.invoke(idle);
        check(LocalSocket.emptyReads==0);check(!LocalSocket.commands.contains("DETACH"));
        LocalSocket.pendingMonitorEvent="<3>AP-STA-CONNECTED 12:34:56:78:90:ab";drain.invoke(idle);
        String idleTrace=Main.text(new File(idleState,"ap-auth-events.txt"));
        check(idleTrace.contains("HOSTAPD_MONITOR_POLL_OK")&&idleTrace.contains("AP-STA-CONNECTED"));
        check(!idleTrace.contains("HOSTAPD_MONITOR_LOST"));
        System.out.println("PASS idle polling avoids KitKat generic IOException receive timeouts and receives a later authentication event without detaching");
        LocalSocket.failAvailable=true;drain.invoke(idle);LocalSocket.failAvailable=false;
        check(Main.text(new File(idleState,"ap-auth-events.txt")).contains("ioctl FIONREAD failed: EBADF"));
        retry.setLong(idle,0);open.invoke(idle);check(Collections.frequency(LocalSocket.commands,"ATTACH")==2);
        idle.close();check(LocalSocket.open==0);
        check(Context.wifi.state==apState && Context.wifi.config.apMode==mode);
        System.out.println("PASS real socket errors retain diagnostic detail and reconnect the monitor while a healthy query socket stays open");
        File bounded=new File(root,"bounded.txt");DiagnosticText.append(bounded,"abcdefgh",5);DiagnosticText.append(bounded,"extra",5);check(bounded.length()==5);
        System.out.println("PASS diagnostic output obeys its byte limit");
    }
}
