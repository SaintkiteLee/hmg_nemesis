package com.saintkite.k5aa;

import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.reflect.Method;

/** Independent, bounded read-only collection; never delays AP rollback. */
public final class NetworkDiagnostics implements Runnable, Closeable {
    private final Main.Device device;
    private final File readRoot;
    private final Thread thread;
    private volatile boolean stopped;
    private volatile HostapdControl query,monitor;
    private String server;
    private long nextControlAttempt,lastProcesses;
    private long monitorAttachedAt,lastMonitorHeartbeat;
    private final File snapshots,controlEvents;
    private static final int SNAPSHOT_LIMIT=4*1024*1024,EVENT_LIMIT=1024*1024;
    NetworkDiagnostics(Main.Device device) {this(device,null);}
    NetworkDiagnostics(Main.Device device,File root) {
        this.device=device;readRoot=root;
        snapshots=new File(device.store.dir,"network-snapshots.txt");controlEvents=new File(device.store.dir,"ap-auth-events.txt");
        thread=new Thread(this,"k5-aa-network-evidence");thread.setDaemon(true);
    }
    File source(String path){return readRoot==null?new File(path):new File(readRoot,path.substring(1));}
    void log(String text) {
        if(stopped)return;
        try {DiagnosticText.append(controlEvents,System.currentTimeMillis()+" "+DiagnosticText.redact(text)+"\n",EVENT_LIMIT);}
        catch(IOException ignored){}
    }
    private void addFile(StringBuilder out,String path,int limit) {
        out.append(path).append('\n');
        File file=source(path);
        out.append("META size=").append(file.length()).append(" mtime=").append(file.lastModified()).append('\n');
        try {out.append(DiagnosticText.read(file,limit)).append('\n');}
        catch(IOException e){out.append("UNAVAILABLE: ").append(DiagnosticText.error(e)).append('\n');}
    }
    Map<String,String> apConfiguration(StringBuilder out) {
        Map<String,String> fields=new LinkedHashMap<String,String>();
        try {
            fields=DiagnosticText.apFields(DiagnosticText.read(source("/data/misc/wifi/hostapd.conf"),32768));
            out.append("HOSTAPD_PUBLIC_CONFIG ").append(fields).append('\n');
        } catch(Exception e){out.append("HOSTAPD_PUBLIC_CONFIG unavailable: ").append(e.getClass().getSimpleName()).append('\n');}
        try {
            Object config=device.config();
            for(String name:new String[]{"SSID","ConnectivitySSID","BSSID","channel","channel24GHzBand","connectivityChannel","is24GHzBand",
                "hiddenSSID","apMode","allowedAuthAlgorithms","allowedGroupCiphers","allowedKeyManagement","allowedPairwiseCiphers","allowedProtocols"}) {
                try {
                    Object value=config.getClass().getField(name).get(config);
                    if("SSID".equals(name) || "ConnectivitySSID".equals(name)) {
                        byte[] bytes=value==null?new byte[0]:value.toString().getBytes("UTF-8");
                        out.append("AP_").append(name).append("_SHA256=").append(Main.sha(bytes)).append(" bytes=").append(bytes.length).append('\n');
                    } else out.append("AP_").append(name).append('=').append(value).append('\n');
                } catch(Exception absent){}
            }
        }catch(Exception e){out.append("AP_CONFIG unavailable: ").append(e.getClass().getSimpleName()).append('\n');}
        try {
            String raw=DiagnosticText.read(source("/data/misc/wifi/hostapd.conf"),32768);
            String mac=null;
            try{mac=DiagnosticText.read(source("/sys/class/net/wlan0/address"),128).trim();}catch(IOException absent){}
            out.append(DiagnosticComparison.compare(raw,device.config(),mac));
        }catch(Exception e){out.append("AP_COMPARE unavailable: ").append(e.getClass().getSimpleName()).append('\n');}
        return fields;
    }
    private void clients(StringBuilder out) {
        try {
            Object result=device.call("getClientList",new Class<?>[0]);
            if(!(result instanceof List)){out.append("OEM_CLIENTS null/unavailable\n");return;}
            List<?> clients=(List<?>)result;out.append("OEM_CLIENTS count=").append(clients.size()).append('\n');
            int count=0;
            for(Object client:clients) {
                if(count++>=16)break;
                for(String name:new String[]{"getHWAddr","getIpAddr","isReachable"}) {
                    try{out.append(name).append('=').append(client.getClass().getMethod(name).invoke(client)).append(' ');}
                    catch(Exception e){out.append(name).append("=unavailable ");}
                }
                out.append('\n');
            }
        }catch(Exception e){out.append("OEM_CLIENTS unavailable: ").append(DiagnosticText.error(e)).append('\n');}
    }
    private void interfaces(StringBuilder out) {
        try {
            Enumeration<NetworkInterface> list=NetworkInterface.getNetworkInterfaces();
            while(list!=null && list.hasMoreElements()) {
                NetworkInterface iface=list.nextElement();if(!iface.getName().matches("(wlan|ap|p2p|swlan|eth)[A-Za-z0-9_.-]*"))continue;
                Enumeration<InetAddress> addresses=iface.getInetAddresses();
                while(addresses.hasMoreElements())out.append("IFACE_ADDRESS ").append(iface.getName()).append(' ').append(addresses.nextElement().getHostAddress()).append('\n');
            }
        }catch(Exception e){out.append("IFACE_ADDRESS unavailable\n");}
    }
    private void processes(StringBuilder out) {
        File[] dirs=source("/proc").listFiles();if(dirs==null)return;
        for(File dir:dirs) {
            if(!dir.getName().matches("[0-9]+"))continue;
            try {
                String command=DiagnosticText.read(new File(dir,"cmdline"),4096);
                String first=command.split("\u0000",2)[0];
                if(!(first.equals(GpmAccess.PACKAGE) || first.endsWith("/hostapd") || first.equals("hostapd") || first.endsWith("/dnsmasq") || first.equals("dnsmasq")))continue;
                out.append("PROCESS pid=").append(dir.getName()).append(" cmd=").append(DiagnosticText.redact(command.replace('\u0000',' '))).append('\n');
                File[] descriptors=new File(dir,"fd").listFiles();if(descriptors==null)continue;
                Object os=Class.forName("libcore.io.Libcore").getField("os").get(null);
                Method readlink=Class.forName("libcore.io.Os").getMethod("readlink",String.class);
                int count=0;
                for(File descriptor:descriptors) {
                    if(count++>=256)break;
                    try {
                        String target=(String)readlink.invoke(os,descriptor.getAbsolutePath());
                        if(target.startsWith("socket:["))out.append("SOCKET_OWNER pid=").append(dir.getName()).append(" fd=").append(descriptor.getName()).append(' ').append(target).append('\n');
                    }catch(Exception unavailable){}
                }
            }catch(Exception e){out.append("PROCESS_READ unavailable pid=").append(dir.getName()).append(' ').append(e.getClass().getSimpleName()).append('\n');}
        }
    }
    void captureOnce(String reason,boolean includeProcesses) {
        StringBuilder out=new StringBuilder("TIME="+System.currentTimeMillis()+" reason="+reason+"\n");
        try{out.append("AP_STATE=").append(device.apState()).append(" AP_MODE=").append(device.mode()).append('\n');}catch(Exception e){out.append("AP_STATE unavailable\n");}
        try{out.append("GPM_STATE ").append(GpmAccess.get().state()).append('\n');}catch(Exception e){out.append("GPM_STATE unavailable\n");}
        Map<String,String> fields=apConfiguration(out);String discovered=DiagnosticText.ctrlServer(fields);
        if(discovered!=null)server=discovered;
        clients(out);interfaces(out);
        for(String path:new String[]{"/proc/net/arp","/proc/net/route","/proc/net/tcp","/proc/net/tcp6","/proc/net/udp","/proc/net/udp6","/proc/net/if_inet6","/proc/net/dev",
            "/data/misc/dnsmasq/dnsmasq.leases","/data/misc/dhcp/dnsmasq.leases","/data/misc/wifi/dnsmasq.leases",
            "/sys/class/net/wlan0/address","/sys/class/net/wlan0/operstate","/sys/class/net/wlan0/carrier"})addFile(out,path,32768);
        if(includeProcesses){addFile(out,"/proc/net/unix",32768);processes(out);}
        if(stopped)return;
        try{DiagnosticText.append(snapshots,out.toString(),SNAPSHOT_LIMIT);}catch(IOException e){log("NETWORK_FILE_WRITE_FAILED "+e.getClass().getSimpleName());}
    }
    private List<String> servers() {
        LinkedHashSet<String> paths=new LinkedHashSet<String>();if(server!=null)paths.add(server);
        paths.add("/data/misc/wifi/hostapd/wlan0");paths.add("/data/misc/wifi/hostapd_ctrl/wlan0");paths.add("/data/misc/wifi/sockets/wlan0");
        try {
            for(String line:DiagnosticText.read(source("/proc/net/unix"),32768).split("\n")) {
                String[] columns=line.trim().split("\\s+");String path=columns[columns.length-1];
                if(path.startsWith("/data/misc/") && path.contains("hostapd") && path.endsWith("/wlan0"))paths.add(path);
            }
        }catch(IOException ignored){}
        return new ArrayList<String>(paths);
    }
    private void openControl() {
        if(stopped || (query!=null && monitor!=null) || System.currentTimeMillis()<nextControlAttempt)return;
        nextControlAttempt=System.currentTimeMillis()+10000;
        if(query!=null){openMonitor(server);return;}
        for(String path:servers()) {
            HostapdControl candidate=null;
            try {
                candidate=new HostapdControl(device.store.dir,path);
                if(!candidate.request("PING").equals("PONG"))throw new IOException("PING not acknowledged");
                synchronized(this){if(!stopped)query=candidate;}
                if(stopped){candidate.close();return;}
                server=path;log("HOSTAPD_QUERY_READY server="+path);
                if(monitor!=null)return; // A query reconnect must preserve the event subscription.
                openMonitor(path);
                return;
            }catch(Exception e){if(candidate!=null)candidate.close();log("HOSTAPD_CONTROL_UNAVAILABLE server="+path+" error="+DiagnosticText.error(e));}
        }
    }
    private void openMonitor(String path) {
        if(stopped || monitor!=null || path==null)return;
        HostapdControl candidate=null;
        try {
            candidate=new HostapdControl(device.store.dir,path);
            if(!candidate.request("ATTACH").equals("OK"))throw new IOException("ATTACH not acknowledged");
            synchronized(this){if(!stopped)monitor=candidate;}
            if(stopped){candidate.close();return;}
            monitorAttachedAt=android.os.SystemClock.elapsedRealtime();lastMonitorHeartbeat=-1;
            log("HOSTAPD_EVENT_MONITOR_ATTACHED");
        }catch(Exception unavailable){if(candidate!=null)candidate.close();log("HOSTAPD_EVENT_MONITOR_UNAVAILABLE "+DiagnosticText.error(unavailable));}
    }
    private void stationSnapshot() {
        HostapdControl active=query;if(active==null)return;
        try {
            Set<String> seen=new HashSet<String>();String response=active.request("STA-FIRST");
            for(int i=0;i<16;i++) {
                String mac=DiagnosticText.firstMac(response);
                if(mac==null){if(i==0)log("HOSTAPD_STATIONS no_station_returned reply="+response);break;}
                if(!seen.add(mac))break;
                for(String line:response.split("\n"))log("HOSTAPD_STA "+mac+" "+line);
                response=active.request("STA-NEXT "+mac);
            }
        }catch(Exception e){
            log("HOSTAPD_QUERY_LOST "+DiagnosticText.error(e));active.close();query=null;
            log("HOSTAPD_EVENT_MONITOR_PRESERVED_AFTER_QUERY_FAILURE");
        }
    }
    private void drainEvents() {
        HostapdControl active=monitor;if(active==null)return;
        long deadline=System.currentTimeMillis()+500;
        for(int i=0;i<16 && System.currentTimeMillis()<deadline;i++) {
            try{
                String event=active.receiveIfAvailable();
                long now=android.os.SystemClock.elapsedRealtime();
                if(lastMonitorHeartbeat<0 || now-lastMonitorHeartbeat>=10000){
                    log("HOSTAPD_MONITOR_POLL_OK attachedMs="+(now-monitorAttachedAt)+" queuedEvent="+(event!=null));lastMonitorHeartbeat=now;
                }
                if(event==null)return;
                log("HOSTAPD_EVENT "+event);
            }catch(Exception e){log("HOSTAPD_MONITOR_LOST "+DiagnosticText.error(e));active.close();monitor=null;return;}
        }
    }
    public void start(){thread.start();}
    public void run() {
        log("NETWORK_DIAGNOSTICS_STARTED interval=2s lease=/data/misc/dnsmasq/dnsmasq.leases AAport=40515");
        while(!stopped) {
            try {
                long now=System.currentTimeMillis();boolean inspect=lastProcesses==0 || now-lastProcesses>=15000;
                captureOnce("wireless authentication, DHCP and AA data trace",inspect);if(inspect)lastProcesses=now;
                openControl();stationSnapshot();drainEvents();
                Thread.sleep(2000);
            }catch(InterruptedException interrupted){return;}
            catch(Throwable failure){log("NETWORK_DIAGNOSTIC_UNAVAILABLE "+failure.getClass().getSimpleName());try{Thread.sleep(2000);}catch(InterruptedException end){return;}}
        }
    }
    public void close() {
        HostapdControl event,commands;
        synchronized(this){stopped=true;event=monitor;commands=query;monitor=null;query=null;}
        // Closing the subscription removes the transient monitor. No AP commands
        // or settings are changed; do not wait for a reply during rollback.
        if(event!=null)event.close();if(commands!=null)commands.close();thread.interrupt();
        try{thread.join(500);}catch(InterruptedException ignored){}
    }
}
