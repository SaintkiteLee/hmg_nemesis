"""Execute the actual restoration code against in-memory Android API doubles."""
from pathlib import Path
import subprocess,tempfile
from toolchain import ROOT, OUT, java_tool
SOURCES={
'android/content/Context.java':'''package android.content;
 public class Context { public static final android.net.wifi.WifiManager wifi=new android.net.wifi.WifiManager();
 public Object getSystemService(String n){return wifi;}
 public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}
 public ContentResolver getContentResolver(){throw new SecurityException("Unable to find app for caller root-test when getting content provider settings");} }''',
'android/content/ContentResolver.java':'''package android.content; public class ContentResolver {
 public int delete(String key,String a,String[] b){android.provider.Settings.Global.values.remove(key);return 1;} }''',
'android/content/ComponentName.java':'package android.content; public class ComponentName { public ComponentName(String a,String b){} }',
'android/content/pm/PackageInfo.java':'package android.content.pm; public class PackageInfo { public ApplicationInfo applicationInfo=new ApplicationInfo(); }',
'android/content/pm/ApplicationInfo.java':'package android.content.pm; public class ApplicationInfo { public static final int FLAG_STOPPED=0x200000; public int flags;public String sourceDir; }',
'android/content/pm/PackageManager.java':'''package android.content.pm; public class PackageManager {
 public static class NameNotFoundException extends Exception{}
 public static int gpmFlags;
 public PackageInfo getPackageInfo(String n,int f)throws NameNotFoundException{if(n.equals("com.google.android.projection.sink")){PackageInfo p=new PackageInfo();p.applicationInfo.flags=gpmFlags;return p;}throw new NameNotFoundException();}
 public Object getActivityInfo(android.content.ComponentName n,int f){return null;} }''',
'android/app/ActivityThread.java':'''package android.app; public class ActivityThread {
 public static ActivityThread systemMain(){
 if(Boolean.getBoolean("k5.test.frameworkThreads")){new Thread(new Runnable(){public void run(){try{Thread.sleep(60000);}catch(Exception ignored){}}}).start();}
 return new ActivityThread();}
 public android.content.Context getSystemContext(){return new android.content.Context();} }''',
'android/os/Looper.java':'package android.os; public class Looper { public static Object myLooper(){return new Object();} public static void prepareMainLooper(){} }',
'android/os/SystemClock.java':'package android.os; public class SystemClock { public static long elapsedRealtime(){return System.currentTimeMillis();} }',
'android/os/IBinder.java':'package android.os; public interface IBinder { boolean isBinderAlive(); }',
'android/os/Binder.java':'package android.os; public class Binder implements IBinder {public boolean isBinderAlive(){return true;}}',
'android/os/Process.java':'package android.os; public class Process {public static int killed=-1;public static void killProcess(int pid){killed=pid;}}',
'android/os/ServiceManager.java':'''package android.os; public class ServiceManager {
 public static GpmBinder current=new GpmBinder();
 public static IBinder checkService(String name){if(!name.equals("gpm"))throw new AssertionError();return current;}
 public static class GpmBinder extends Binder implements com.hkmc.gpm.IGpmInterfaces {
 public int mode,status,statusReads;public boolean changingStatus;
 public int getGPMConnectedMode(){return mode;}
 public int getGpmStatus(){return changingStatus?statusReads++:status;} } }''',
'com/hkmc/gpm/IGpmInterfaces.java':'''package com.hkmc.gpm; public interface IGpmInterfaces {
 int getGPMConnectedMode();int getGpmStatus();public class Stub {public static IGpmInterfaces asInterface(android.os.IBinder binder){return (IGpmInterfaces)binder;}} }''',
'android/os/Bundle.java':'''package android.os; public class Bundle {
 private final java.util.Map<String,Object> values=new java.util.HashMap<String,Object>();
 public void putString(String k,String v){values.put(k,v);}public String getString(String k){return (String)values.get(k);}
 public void putInt(String k,int v){values.put(k,v);}public int getInt(String k){return (Integer)values.get(k);}
 public boolean containsKey(String k){return values.containsKey(k);} }''',
'android/net/Uri.java':'''package android.net; public class Uri {
 private String value;public static Uri parse(String s){Uri u=new Uri();u.value=s;return u;}public String toString(){return value;} }''',
'android/net/LocalSocketAddress.java':'''package android.net; public class LocalSocketAddress {
 public enum Namespace { FILESYSTEM, ABSTRACT } public final String path;public final Namespace namespace;
 public LocalSocketAddress(String p,Namespace n){path=p;namespace=n;} }''',
'android/net/LocalSocket.java':'''package android.net; import java.io.*;import java.util.*;public class LocalSocket {
 public static final int SOCKET_DGRAM=1;public static volatile int open,emptyReads;public static volatile boolean failConnect,eofStations,failAvailable;
 public static volatile String pendingMonitorEvent;
 public static final List<String> commands=Collections.synchronizedList(new ArrayList<String>());
 private boolean bound,monitorSocket;private volatile boolean closed;private final Queue<String> replies=new LinkedList<String>();
 public LocalSocket(int type){if(type!=1)throw new AssertionError();synchronized(LocalSocket.class){open++;}}
 public void bind(LocalSocketAddress address)throws IOException{if(address.namespace!=LocalSocketAddress.Namespace.ABSTRACT)throw new IOException("private filesystem client is inaccessible to wifi UID");bound=true;}
 public void setSoTimeout(int timeout)throws IOException{if(!bound)throw new IOException("socket must be created by bind first");}
 public void connect(LocalSocketAddress address)throws IOException{if(!bound)throw new IOException("not bound");if(failConnect)throw new IOException("test: unavailable control endpoint");}
 public InputStream getInputStream(){return new InputStream(){
 public int available()throws IOException{if(closed)throw new IOException("closed");if(failAvailable)throw new IOException("ioctl FIONREAD failed: EBADF");
 synchronized(replies){if(monitorSocket&&pendingMonitorEvent!=null){replies.add(pendingMonitorEvent);pendingMonitorEvent=null;}
 String next=replies.peek();return next==null?0:next.getBytes("UTF-8").length;}}
 public int read()throws IOException{throw new IOException("use datagram read");}
 public int read(byte[] out)throws IOException{if(closed)throw new IOException("closed");String value;
 synchronized(replies){value=replies.poll();}if(value==null){emptyReads++;throw new IOException("recvmsg failed: EAGAIN (Try again)");}
 if(value.equals("EOF_TEST"))return -1;
 byte[] bytes=value.getBytes("UTF-8");System.arraycopy(bytes,0,out,0,bytes.length);return bytes.length;}};}
 public OutputStream getOutputStream(){return new OutputStream(){
 public void write(int b)throws IOException{throw new IOException("use datagram write");}
 public void write(byte[] bytes)throws IOException{if(closed)throw new IOException("closed");String command=new String(bytes,"UTF-8");commands.add(command);String reply;
 if(command.equals("PING"))reply="PONG";else if(command.equals("ATTACH")||command.equals("DETACH"))reply="OK";
 else if(command.equals("STA-FIRST"))reply="12:34:56:78:90:ab\\nflags=[AUTH][ASSOC][AUTHORIZED]\\naid=1";
 else reply="FAIL";if(command.equals("STA-FIRST")&&eofStations)reply="EOF_TEST";
 if(command.equals("ATTACH"))monitorSocket=true;
 synchronized(replies){replies.add(reply);if(command.equals("ATTACH")&&eofStations)replies.add("<3>AP-STA-CONNECTED 12:34:56:78:90:ab");}}};}
 public void close()throws IOException{if(!closed){closed=true;synchronized(LocalSocket.class){open--;}}}
 }''',
'android/content/IContentProvider.java':'''package android.content; public interface IContentProvider {
 android.os.Bundle call(String caller,String method,String key,android.os.Bundle args);
 int delete(String caller,android.net.Uri uri,String selection,String[] args); }''',
'android/app/IActivityManager.java':'''package android.app; public interface IActivityManager {
 public class ContentProviderHolder {public android.content.IContentProvider provider;}
 ContentProviderHolder getContentProviderExternal(String authority,int user,android.os.IBinder token);
 void removeContentProviderExternal(String authority,android.os.IBinder token); }''',
'android/app/ActivityManagerNative.java':'''package android.app; public class ActivityManagerNative {
 public static int acquired,released;public static boolean failRead;
 public static IActivityManager getDefault(){return new Manager();}
 public static class Manager implements IActivityManager {
 public ContentProviderHolder getContentProviderExternal(String authority,int user,android.os.IBinder token){
 if(!authority.equals("settings")||user!=0||token==null)throw new AssertionError();
 acquired++;ContentProviderHolder holder=new ContentProviderHolder();holder.provider=new Provider();return holder;}
 public void removeContentProviderExternal(String authority,android.os.IBinder token){released++;} }
 public static class Provider implements android.content.IContentProvider {
 public android.os.Bundle call(String caller,String method,String key,android.os.Bundle args){
 if(caller!=null||args.getInt("_user")!=0)throw new AssertionError();
 if(failRead)throw new SecurityException("provider read failed");
 android.os.Bundle reply=new android.os.Bundle();
 if(method.equals("GET_global"))reply.putString("value",android.provider.Settings.Global.values.get(key));
 else if(method.equals("PUT_global"))android.provider.Settings.Global.values.put(key,args.getString("value"));else throw new AssertionError();
 return reply;}
 public int delete(String caller,android.net.Uri uri,String selection,String[] args){
 if(caller!=null||!uri.toString().equals("content://settings/global")||!selection.equals("name=?")||args.length!=1)throw new AssertionError();
 android.provider.Settings.Global.values.remove(args[0]);return 1;} } }''',
'android/os/SystemProperties.java':'''package android.os; public class SystemProperties {
 public static final java.util.Map<String,String> values=new java.util.HashMap<String,String>();
 public static String get(String key){String v=values.get(key);return v==null?"":v;}
 public static void set(String key,String value){values.put(key,value);} }''',
'android/os/Parcelable.java':'package android.os; public interface Parcelable { interface Creator<T>{T createFromParcel(Parcel p);} }',
'android/os/Parcel.java':'''package android.os; public class Parcel { public byte[] data;
 public static Parcel obtain(){return new Parcel();} public byte[] marshall(){return data.clone();}
 public void unmarshall(byte[] b,int o,int n){data=java.util.Arrays.copyOfRange(b,o,o+n);}public void setDataPosition(int x){}public void recycle(){} }''',
'android/provider/Settings.java':'''package android.provider; public class Settings { public static class Global {
 public static final java.util.Map<String,String> values=new java.util.HashMap<String,String>();
 public static String getString(android.content.ContentResolver cr,String k){return values.get(k);}
 public static boolean putString(android.content.ContentResolver cr,String k,String v){values.put(k,v);return true;}
 public static String getUriFor(String k){return k;} } }''',
'android/net/wifi/WifiConfiguration.java':'package android.net.wifi; public class WifiConfiguration {\n public String SSID="original",preSharedKey="local-test-only",ConnectivitySSID="AA-trial",ConnectivityPSK="secret-test-123";\n public int apMode=0,authType=4; public int getAuthType(){return authType;}\n public WifiConfiguration copy(){WifiConfiguration x=new WifiConfiguration();x.SSID=SSID;x.preSharedKey=preSharedKey;x.ConnectivitySSID=ConnectivitySSID;x.ConnectivityPSK=ConnectivityPSK;x.apMode=apMode;x.authType=authType;return x;}\n public void writeToParcel(android.os.Parcel p,int f){try{p.data=(SSID+"|"+preSharedKey+"|"+ConnectivitySSID+"|"+ConnectivityPSK+"|"+apMode+"|"+authType).getBytes("UTF-8");}catch(Exception e){throw new RuntimeException(e);}}\n public static final android.os.Parcelable.Creator<WifiConfiguration> CREATOR=new android.os.Parcelable.Creator<WifiConfiguration>(){\n public WifiConfiguration createFromParcel(android.os.Parcel p){try{String[] s=new String(p.data,"UTF-8").split("[|]",-1);\n WifiConfiguration c=new WifiConfiguration();c.SSID=s[0];c.preSharedKey=s[1];c.ConnectivitySSID=s[2];c.ConnectivityPSK=s[3];c.apMode=Integer.parseInt(s[4]);c.authType=Integer.parseInt(s[5]);return c;\n }catch(Exception e){throw new RuntimeException(e);}}}; }',
'android/net/wifi/WifiManager.java':'''package android.net.wifi; public class WifiManager {
 public WifiConfiguration config=new WifiConfiguration();public int state=13;public boolean client=false;
 public java.util.List<?> clients=java.util.Collections.emptyList();public java.util.List<?> getClientList(){return clients;}
 public boolean ignoreRestore=false;
 public int getWifiApState(){return state;} public int getWifiApMode(){return config.apMode;}
 public WifiConfiguration getWifiApConfiguration(){return config.copy();}
 public void setWifiApConfiguration(WifiConfiguration c){if(!ignoreRestore)config=c.copy();}
 public boolean setWifiApEnabledForConnectivity(WifiConfiguration c,boolean on){state=on?13:11;if(on){config=c.copy();config.apMode=2;}
 android.provider.Settings.Global.values.put("wifi_ap_on",on?"1":"0");return true;}
 public boolean setWifiApEnabled(WifiConfiguration c,boolean on){state=on?13:11;if(on){config=c.copy();config.apMode=1;}
 android.provider.Settings.Global.values.put("wifi_ap_on",on?"1":"0");return true;}
 public boolean isWifiEnabled(){return client;}public boolean setWifiEnabled(boolean value){client=value;android.provider.Settings.Global.values.put("wifi_on",value?"1":"0");return true;} }''',
'com/saintkite/k5aa/RestoreTest.java':'''package com.saintkite.k5aa;
 import java.io.*;import java.util.*;import android.net.wifi.*;import android.content.Context;import android.provider.Settings;
 public class RestoreTest {
 static void check(boolean x){if(!x)throw new AssertionError();}
 public static void main(String[] a)throws Exception{
  File root=new File(a[0]);
  for(int scenario=0;scenario<4;scenario++){
   File dir=new File(root,"case"+scenario);dir.mkdirs();
   Main.Disk store=new Main.Disk(dir);WifiManager wifi=Context.wifi;
   wifi.ignoreRestore=false;wifi.config=new WifiConfiguration();wifi.config.preSharedKey="changed-by-trial";wifi.config.apMode=2;wifi.state=13;wifi.client=false;
   Settings.Global.values.clear();Settings.Global.values.put("wifi_ap_on","1");Settings.Global.values.put("wifi_on","0");
   android.os.SystemProperties.set("sys.boot_completed","0");
   WifiConfiguration original=new WifiConfiguration();original.apMode=scenario==1?1:0;
   android.os.Parcel parcel=android.os.Parcel.obtain();original.writeToParcel(parcel,0);byte[] saved=parcel.marshall();
   Main.write(new File(dir,"original-ap.parcel"),saved);
   Properties props=new Properties();props.setProperty("configSha",Main.sha(saved));props.setProperty("bridgeSha","unused");
   props.setProperty("sys.boot_completed","1");
   props.setProperty("apEnabled",scenario==1?"true":"false");props.setProperty("apMode",Integer.toString(original.apMode));
   props.setProperty("wifiEnabled",scenario==2?"true":"false");props.setProperty("wifi_ap_on",scenario==1?"1":"ABSENT");props.setProperty("wifi_on",scenario==2?"1":"0");
   ByteArrayOutputStream out=new ByteArrayOutputStream();props.store(out,"");Main.write(new File(dir,"original.properties"),out.toByteArray());
   if(scenario==3)Main.write(new File(dir,"original-ap.parcel"),new byte[]{1,2,3});
   Main.Device device=new Main.Device(store,null);
   if(scenario==3){try{device.restore();throw new AssertionError();}catch(IOException expected){}check(wifi.state==13);System.out.println("PASS corrupt AP backup rejected before mutation");continue;}
   device.restore();check(wifi.config.preSharedKey.equals(original.preSharedKey));check(wifi.config.apMode==original.apMode);
   check("1".equals(android.os.SystemProperties.get("sys.boot_completed")));
   check(wifi.state==(scenario==1?13:11));check(wifi.client==(scenario==2));
   check(scenario==1?"1".equals(Settings.Global.values.get("wifi_ap_on")):!Settings.Global.values.containsKey("wifi_ap_on"));
   check(Settings.Global.values.get("wifi_on").equals(scenario==2?"1":"0"));
   System.out.println("PASS actual adapter restores private AP configuration and exact settings: case "+scenario);
   Settings.Global.values.put("wifi_on","unexpected");
   try{device.verifyRestored();throw new AssertionError();}catch(IOException expected){}
   check("unexpected".equals(Settings.Global.values.get("wifi_on")));
   Settings.Global.values.put("wifi_on",scenario==2?"1":"0");
   wifi.config.preSharedKey="different-after-restore";
   try{device.verifyRestored();throw new AssertionError();}catch(IOException expected){}
   check(wifi.config.preSharedKey.equals("different-after-restore"));wifi.config.preSharedKey=original.preSharedKey;
   device.verifyRestored();
   System.out.println("PASS independent verification rejects changed settings or AP credentials without altering either: case "+scenario);
  }
  check(android.app.ActivityManagerNative.acquired==android.app.ActivityManagerNative.released);
  System.out.println("PASS root settings use external provider with exact keys and balanced release");
  android.app.ActivityManagerNative.failRead=true;
  try{ExternalSettings.get("wifi_ap_on");throw new AssertionError();}catch(java.lang.reflect.InvocationTargetException expected){}
  check(android.app.ActivityManagerNative.acquired==android.app.ActivityManagerNative.released);
  System.out.println("PASS external provider released after exception");
  android.app.ActivityManagerNative.failRead=false;
 }}'''
}
SOURCES['com/saintkite/k5aa/GpmRestartTest.java']='''package com.saintkite.k5aa;
import java.io.*;import android.os.ServiceManager;import android.content.pm.PackageManager;import android.content.pm.ApplicationInfo;
public class GpmRestartTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static class Sim extends Main.Device {
  long clock;int stops,starts;boolean failStart,keepOld;
  Sim(Main.Disk s)throws Exception{super(s,null);}
  public long now(){return clock;}public void pause(long n){clock+=n;}
  int command(String...a)throws Exception {
   if(!a[0].equals("/system/bin/am"))throw new AssertionError();
   if(a[1].equals("force-stop")){stops++;PackageManager.gpmFlags|=ApplicationInfo.FLAG_STOPPED;if(!keepOld)ServiceManager.current=null;return 0;}
   if(a[1].equals("startservice")){starts++;if(failStart)return 1;if(!keepOld||ServiceManager.current==null)ServiceManager.current=new ServiceManager.GpmBinder();PackageManager.gpmFlags=0;return 0;}
   throw new AssertionError();
  }
 }
 static Sim seed(File root,String name)throws Exception{
  File dir=new File(root,name);dir.mkdirs();
  for(String f:new String[]{"original.properties","original-ap.parcel"})Main.write(new File(dir,f),Main.read(new File(new File(root,"case0"),f)));
  ServiceManager.current=new ServiceManager.GpmBinder();PackageManager.gpmFlags=0;
  return new Sim(new Main.Disk(dir));
 }
 public static void main(String[] a)throws Exception{
  File root=new File(a[0]);Sim d=seed(root,"gpm-usb");
  File usb=new File(root,"media/aa");usb.mkdirs();File marker=new File(usb,"trial-media-id");Main.write(marker,"media-test".getBytes("UTF-8"));
  d.original.setProperty("usbRoot",usb.getParent());d.original.setProperty("mediaId","media-test");
  ServiceManager.current.mode=1;ServiceManager.current.status=0;
  d.snapshotGpmState();check(d.stops==0&&d.starts==0);
  check(d.protectedMode()==0);
  check(!d.prepareWireless()&&d.stops==0);check(marker.delete());
  check(!d.prepareWireless());d.clock=1999;check(!d.prepareWireless());d.clock=2000;check(d.prepareWireless());
  check(d.stops==1&&d.starts==1&&PackageManager.gpmFlags==0);check(d.prepareWireless()&&d.stops==1);
  System.out.println("PASS retained USB startMode=1 with status=0 resets only after USB removal, with a fresh live GPM Binder");
  for(int mode=1;mode<=2;mode++){
   d=seed(root,"gpm-active-"+mode);ServiceManager.current.mode=mode;ServiceManager.current.status=6;
   try{d.snapshotGpmState();throw new AssertionError();}catch(IOException expected){}
   try{d.restartIdleGpm();throw new AssertionError();}catch(IOException expected){}check(d.stops==0);
  }
  System.out.println("PASS actual controller refuses to stop both active wired and wireless AA sessions");
  d=seed(root,"gpm-changing");ServiceManager.current.mode=1;ServiceManager.current.changingStatus=true;
  try{d.restartIdleGpm();throw new AssertionError();}catch(IOException expected){}check(d.stops==0);
  System.out.println("PASS changing AA status aborts before any process stop");
  d=seed(root,"gpm-unknown");ServiceManager.current.mode=1;ServiceManager.current.status=7;
  try{d.snapshotGpmState();throw new AssertionError();}catch(IOException expected){}check(d.stops==0);
  System.out.println("PASS preflight protects unknown OEM status values without a service stop");
  d=seed(root,"gpm-stopped");PackageManager.gpmFlags=ApplicationInfo.FLAG_STOPPED;
  try{d.snapshotGpmState();throw new AssertionError();}catch(IOException expected){}check(d.stops==0);
  System.out.println("PASS originally stopped AA package is left untouched by preflight");
  d=seed(root,"gpm-start-fail");d.failStart=true;
  try{d.restartIdleGpm();throw new AssertionError();}catch(IOException expected){}
  check(ServiceManager.current==null&&(PackageManager.gpmFlags&ApplicationInfo.FLAG_STOPPED)!=0);
  d.failStart=false;d.restore();check(ServiceManager.current!=null&&PackageManager.gpmFlags==0);
  System.out.println("PASS failed AA restart restores the original runnable package state and AP settings");
  d=seed(root,"gpm-same-binder");d.keepOld=true;
  try{d.restartIdleGpm();throw new AssertionError();}catch(IOException expected){}
  check(d.clock>=20000);d.keepOld=false;d.restore();
  System.out.println("PASS unchanged GPM Binder is not reported as a successful reset");
 }
}'''
SOURCES['com/saintkite/k5aa/GpmIdentityTest.java']='''package com.saintkite.k5aa;
import java.io.*;
public class GpmIdentityTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static void fixture(File root,String cmd,String uid,String start)throws Exception{
  File dir=new File(root,"757");dir.mkdirs();
  Main.write(new File(dir,"cmdline"),(cmd+"\\0").getBytes("UTF-8"));
  Main.write(new File(dir,"status"),("Name: test\\nUid:\\t"+uid+"\\t"+uid+"\\t"+uid+"\\t"+uid+"\\n").getBytes("UTF-8"));
  StringBuilder stat=new StringBuilder("757 (gpm) S");for(int i=1;i<19;i++)stat.append(" 0");stat.append(" ").append(start).append(" 0\\n");
  Main.write(new File(dir,"stat"),stat.toString().getBytes("UTF-8"));
 }
 public static void main(String[] a)throws Exception{
  File root=new File(a[0]);root.mkdirs();
  fixture(root,"system_server","1000","123");check(GpmProcess.find(root)==null);check(android.os.Process.killed==-1);
  fixture(root,GpmAccess.PACKAGE,"1001","123");check(GpmProcess.find(root)==null);
  fixture(root,GpmAccess.PACKAGE,"1000","123");GpmProcess.Identity before=GpmProcess.find(root);check(before!=null);
  fixture(root,GpmAccess.PACKAGE,"1000","456");check(!GpmProcess.killIfStillSame(root,before));check(android.os.Process.killed==-1);
  before=GpmProcess.find(root);check(GpmProcess.killIfStillSame(root,before));check(android.os.Process.killed==757);
  System.out.println("PASS persistent AA process reset requires exact command, all UIDs and unchanged PID start time");
 }
}'''
def main():
 with tempfile.TemporaryDirectory(prefix='k5-aa-adapter-') as temp:
  root=Path(temp)
  for name,code in SOURCES.items():
   p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,encoding='utf-8')
  # The Java regular-expression string must contain one escaped literal pipe.
  p=root/'android/net/wifi/WifiConfiguration.java'
  p.write_text(p.read_text().replace('split("\\\\|",-1)','split("[|]",-1)'),encoding='utf-8')
  cmd=[str(java_tool('javac')),'-encoding','UTF-8','-d',str(root/'classes')]
  cmd += [str(p) for p in root.rglob('*.java')]+[str(p) for p in (ROOT/'src').rglob('*.java')]+[str(ROOT/'tests/NetworkDiagnosticsTest.java'),str(ROOT/'tests/ApComparisonTest.java'),str(ROOT/'tests/ApCredentialsTest.java')]
  p=subprocess.run(cmd,capture_output=True,text=True,encoding='utf-8',errors='replace')
  assert p.returncode==0,(p.stdout,p.stderr)
  p=subprocess.run([str(java_tool('java')),'-cp',str(root/'classes'),'com.saintkite.k5aa.RestoreTest',str(root/'state')],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=35)
  assert p.returncode==0,(p.stdout,p.stderr)
  print(p.stdout,end='')
  lines=p.stdout
  p=subprocess.run([str(java_tool('java')),'-cp',str(root/'classes'),'com.saintkite.k5aa.GpmRestartTest',str(root/'state')],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=10)
  assert p.returncode==0,(p.stdout,p.stderr);print(p.stdout,end='');lines+=p.stdout
  p=subprocess.run([str(java_tool('java')),'-cp',str(root/'classes'),'com.saintkite.k5aa.NetworkDiagnosticsTest',str(root/'network-fixture')],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=12)
  assert p.returncode==0,(p.stdout,p.stderr);print(p.stdout,end='');lines+=p.stdout
  p=subprocess.run([str(java_tool('java')),'-cp',str(root/'classes'),'com.saintkite.k5aa.ApComparisonTest'],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=10)
  assert p.returncode==0,(p.stdout,p.stderr);print(p.stdout,end='');lines+=p.stdout
  p=subprocess.run([str(java_tool('java')),'-cp',str(root/'classes'),'com.saintkite.k5aa.ApCredentialsTest',str(root/'alignment-fixture')],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=10)
  assert p.returncode==0,(p.stdout,p.stderr);print(p.stdout,end='');lines+=p.stdout
  p=subprocess.run([str(java_tool('java')),'-cp',str(root/'classes'),'com.saintkite.k5aa.GpmIdentityTest',str(root/'fake-proc')],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=10)
  assert p.returncode==0,(p.stdout,p.stderr);print(p.stdout,end='');lines+=p.stdout
  # Actual entry point must exit even after Android creates non-daemon threads.
  for mode,expected in [('status',0),('snapshot',1)]:
   p=subprocess.run([str(java_tool('java')),'-Dk5.test.frameworkThreads=true','-cp',str(root/'classes'),'com.saintkite.k5aa.Main',mode,str(root/('entry-'+mode)),'test-sha',str(root/'usb'),'test-id'],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=4)
   assert p.returncode==expected,(mode,p.stdout,p.stderr)
   assert 'K5AA_ENTRY' in p.stderr
   if expected:assert 'K5AA_FAILED' in p.stderr and 'Wrong firmware' in p.stderr
   line='PASS actual entry point exits with framework threads: '+mode;print(line);lines+=line+'\n'
  (OUT/'adapter-test-results.txt').write_text(lines,encoding='utf-8')
if __name__=='__main__':main()
