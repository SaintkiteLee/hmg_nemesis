package com.saintkite.k5aa;
public final class InstallationTest {
    static class S implements Installation.Store {
        String phase="armed",decision="";boolean failCommit;
        public String phase(){return phase;}
        public void phase(String value)throws Exception{if(failCommit && value.equals("installed"))throw new Exception();phase=value;}
        public String decision(){return decision;}
        public void event(String text){}
    }
    static class D implements Installation.Device {
        final S s;long clock;int starts,installs,restores,checks;
        boolean failInstall,failAp,failRestore,failPrepare,usbPresent,drift,cancel;
        D(S state){s=state;}
        public void installBridge()throws Exception{installs++;if(failInstall)throw new Exception();}
        public void startAp()throws Exception{starts++;if(failAp)throw new Exception();}
        public boolean apReady(){return !(drift && ++checks>1);}
        public boolean prepareWireless()throws Exception{if(failPrepare)throw new Exception();return !usbPresent;}
        public void restore()throws Exception{restores++;if(failRestore)throw new Exception();}
        public void pause(long n){clock+=n;if(cancel)s.decision="rollback";}
        public long now(){return clock;}
    }
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        for(String scenario:new String[]{"success","installFailure","apFailure","restartFailure","drift","usbTimeout","cancel","commitFailure"}) {
            S s=new S();D d=new D(s);
            d.failInstall=scenario.equals("installFailure");d.failAp=scenario.equals("apFailure");
            d.failPrepare=scenario.equals("restartFailure");d.drift=scenario.equals("drift");
            d.usbPresent=scenario.equals("usbTimeout")||scenario.equals("cancel");d.cancel=scenario.equals("cancel");
            s.failCommit=scenario.equals("commitFailure");Installation.run(s,d,true,2000);
            check(s.phase.equals(scenario.equals("success")?"installed":"rolled_back"));
            check(d.restores==(scenario.equals("success")?0:1));
            System.out.println("PASS installation transaction: "+scenario);
        }
        for(String phase:new String[]{"applying","waiting_usb","restoring"}) {
            S s=new S();s.phase=phase;D d=new D(s);Installation.run(s,d,true,2000);
            check(s.phase.equals("rolled_back")&&d.installs==0&&d.starts==0);
            System.out.println("PASS interrupted "+phase+" restores without reapply");
        }
        S s=new S();D d=new D(s);Installation.run(s,d,false,2000);
        check(s.phase.equals("rolled_back")&&d.starts==0);System.out.println("PASS boot change restores");
        s=new S();d=new D(s);d.failAp=true;d.failRestore=true;
        try{Installation.run(s,d,true,2000);throw new AssertionError();}catch(Exception expected){}
        check(s.phase.equals("restoring"));d.failRestore=false;Installation.run(s,d,true,2000);
        check(s.phase.equals("rolled_back")&&d.starts==1);System.out.println("PASS failed restoration retries without false completion");
        for(String phase:new String[]{"installed","rolled_back"}) {
            s=new S();s.phase=phase;d=new D(s);Installation.run(s,d,false,2000);
            check(d.installs==0&&d.starts==0&&d.restores==0);System.out.println("PASS completed "+phase+" is idempotent");
        }
    }
}
