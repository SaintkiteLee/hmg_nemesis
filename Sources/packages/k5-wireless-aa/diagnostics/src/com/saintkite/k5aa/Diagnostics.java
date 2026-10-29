package com.saintkite.k5aa;
import java.io.*;
/** Explicit opt-in, bounded read-only collection. Never included in the normal USB. */
public final class Diagnostics {
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
    public static void main(String[] args) {
        int rc=0; NetworkDiagnostics network=null; Capture capture=null;
        try {
            if(args.length!=1)throw new IllegalArgumentException("output-directory required");
            if(android.os.Looper.myLooper()==null)android.os.Looper.prepareMainLooper();
            File dir=new File(args[0]);if(!dir.mkdirs() && !dir.isDirectory())throw new IOException("Output unavailable");
            dir.setReadable(false,false);dir.setWritable(false,false);dir.setExecutable(false,false);
            dir.setReadable(true,true);dir.setWritable(true,true);dir.setExecutable(true,true);
            Main.Device device=new Main.Device(new Main.Disk(dir),null);
            network=new NetworkDiagnostics(device);network.start();capture=new Capture(dir);
            Thread.sleep(60000);
        } catch(Throwable error){System.err.println("DIAGNOSTICS_FAILED "+error.getClass().getSimpleName());rc=1;}
        finally {if(network!=null)network.close();if(capture!=null)capture.close();System.exit(rc);}
    }
}
