package com.saintkite.k5aa;
public final class TrialTest {
    static class S implements Trial.Store {
        String phase="armed",decision="";boolean failCommit;
        public String phase(){return phase;}
        public void phase(String p) throws Exception {if(failCommit && p.equals("committed"))throw new Exception("storage error");phase=p;}
        public String decision(){return decision;}
        public void event(String t){}
    }
    static class D implements Trial.Device {
        final S s;long clock;int installs,starts,restores,checks;boolean failInstall,failAp,failRestore,dropAtKeep;
        String duringWait="";boolean prepareReady=true,failPrepare;
        D(S state){s=state;}
        public void installBridge() throws Exception {installs++;if(failInstall)throw new Exception("package failure");}
        public void startAp() throws Exception {starts++;if(failAp)throw new Exception("AP failure");}
        public boolean apReady(){checks++;return !(dropAtKeep && checks>1);}
        public boolean prepareWireless() throws Exception {if(failPrepare)throw new Exception("AA restart failure");return prepareReady;}
        public void restore() throws Exception {restores++;if(failRestore)throw new Exception("restore unavailable");}
        public void pause(long n){clock+=n;if(!duringWait.isEmpty())s.decision=duringWait;}
        public long now(){return clock;}
    }
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void passed(String n){System.out.println("PASS "+n);}
    public static void main(String[] args) throws Exception {
        S s=new S();D d=new D(s);d.duringWait="keep";Trial.run(s,d,true,8000);
        check(s.phase.equals("committed")&&d.restores==0);passed("explicit real-AA confirmation keeps ready AP");
        s=new S();d=new D(s);Trial.run(s,d,true,3000);
        check(s.phase.equals("rolled_back")&&d.restores==1);passed("no confirmation times out and restores");
        s=new S();d=new D(s);d.failInstall=true;Trial.run(s,d,true,3000);
        check(s.phase.equals("rolled_back")&&d.starts==0&&d.restores==1);passed("package install failure restores");
        s=new S();d=new D(s);d.failAp=true;Trial.run(s,d,true,3000);
        check(s.phase.equals("rolled_back")&&d.restores==1);passed("AP failure restores");
        s=new S();d=new D(s);d.duringWait="rollback";Trial.run(s,d,true,3000);
        check(s.phase.equals("rolled_back"));passed("user reports failed AA: restores");
        s=new S();d=new D(s);d.duringWait="keep";d.dropAtKeep=true;Trial.run(s,d,true,3000);
        check(s.phase.equals("rolled_back"));passed("keep rejected when AP is no longer ready");
        s=new S();d=new D(s);d.failPrepare=true;Trial.run(s,d,true,3000);
        check(s.phase.equals("rolled_back")&&d.restores==1);passed("AA recovery failure restores AP and settings");
        s=new S();d=new D(s);d.prepareReady=false;d.duringWait="keep";Trial.run(s,d,true,3000);
        check(s.phase.equals("rolled_back")&&d.restores==1);passed("keep rejected before USB-removal recovery completes");
        for(String pending:new String[]{"applying","testing","restoring"}){
            s=new S();s.phase=pending;d=new D(s);Trial.run(s,d,true,3000);
            check(s.phase.equals("rolled_back")&&d.starts==0&&d.installs==0);passed("interrupted "+pending+" recovers without reapplying");
        }
        s=new S();d=new D(s);Trial.run(s,d,false,3000);
        check(s.phase.equals("rolled_back")&&d.starts==0);passed("changed boot restores before apply");
        s=new S();s.decision="rollback";d=new D(s);Trial.run(s,d,true,3000);
        check(s.phase.equals("rolled_back")&&d.installs==0);passed("early cancellation never applies");
        s=new S();d=new D(s);d.failAp=true;d.failRestore=true;
        try{Trial.run(s,d,true,3000);throw new AssertionError();}catch(Exception expected){}
        check(s.phase.equals("restoring"));d.failRestore=false;Trial.run(s,d,true,3000);
        check(s.phase.equals("rolled_back")&&d.starts==1);passed("failed restore remains pending and retries without reapplying");
        s=new S();s.failCommit=true;d=new D(s);d.duringWait="keep";Trial.run(s,d,true,3000);
        check(s.phase.equals("rolled_back"));passed("failed commit persistence restores");
        for(String done:new String[]{"committed","rolled_back"}){
            s=new S();s.phase=done;d=new D(s);Trial.run(s,d,true,3000);
            check(d.starts==0&&d.installs==0&&d.restores==0);passed("completed "+done+" is idempotent");
        }
    }
}
