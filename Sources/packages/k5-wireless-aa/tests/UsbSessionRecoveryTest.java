package com.saintkite.k5aa;
public final class UsbSessionRecoveryTest {
    static class B implements UsbSessionRecovery.Backend {
        boolean present=true,fail;long clock;int mode,resets;
        public boolean mediaPresent(){return present;}
        public int protectedMode(){return mode;}
        public void restartIdleGpm() throws Exception {resets++;if(fail)throw new Exception("failed restart");}
        public long now(){return clock;}
    }
    static void check(boolean b){if(!b)throw new AssertionError();}
    static void pass(String s){System.out.println("PASS "+s);}
    public static void main(String[] a)throws Exception {
        B b=new B();UsbSessionRecovery r=new UsbSessionRecovery(b);
        for(int i=0;i<10;i++){b.clock+=1000;check(!r.step());}check(b.resets==0);pass("USB present never restarts AA");
        b.present=false;check(!r.step());b.clock+=1999;check(!r.step());check(b.resets==0);
        b.clock++;check(r.step()&&b.resets==1);b.clock+=10000;check(r.step()&&b.resets==1);pass("stable USB removal restarts an idle AA service exactly once");
        b=new B();b.present=false;r=new UsbSessionRecovery(b);check(!r.step());b.clock=1500;b.present=true;check(!r.step());b.present=false;check(!r.step());b.clock=3000;check(!r.step());b.clock=3500;check(r.step());pass("USB return restarts the absence timer");
        b=new B();b.present=false;b.mode=2;r=new UsbSessionRecovery(b);r.step();b.clock=2000;check(r.step()&&b.resets==0);pass("active wireless AA is preserved");
        b=new B();b.present=false;b.mode=1;r=new UsbSessionRecovery(b);r.step();b.clock=2000;
        try{r.step();throw new AssertionError();}catch(IllegalStateException expected){}check(b.resets==0);pass("active wired AA blocks setup reset");
        b=new B();b.present=false;b.fail=true;r=new UsbSessionRecovery(b);r.step();b.clock=2000;
        try{r.step();throw new AssertionError();}catch(Exception expected){}check(b.resets==1);pass("reset failure is propagated for rollback");
        b=new B();b.present=false;b.mode=99;r=new UsbSessionRecovery(b);r.step();b.clock=2000;
        try{r.step();throw new AssertionError();}catch(IllegalStateException expected){}check(b.resets==0);pass("unknown AA state never restarts service");
    }
}
