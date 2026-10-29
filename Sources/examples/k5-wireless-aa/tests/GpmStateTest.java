package com.saintkite.k5aa;

import java.io.IOException;

public final class GpmStateTest {
    static void check(boolean b){if(!b)throw new AssertionError();}
    static void rejected(int mode,int status)throws Exception {
        try{new GpmState(mode,status).protectedMode();throw new AssertionError();}
        catch(IOException expected){}
    }
    public static void main(String[] a)throws Exception {
        for(int mode=0;mode<=2;mode++)check(new GpmState(mode,0).protectedMode()==0);
        System.out.println("PASS an uninitialized AA session is idle despite a retained wired/wireless start mode");
        for(int status=1;status<=6;status++) {
            check(new GpmState(1,status).protectedMode()==1);
            check(new GpmState(2,status).protectedMode()==2);
            rejected(0,status);
        }
        System.out.println("PASS every nonzero known AA status is protected from idle restart");
        rejected(-1,0);rejected(3,0);rejected(1,-1);rejected(1,7);
        System.out.println("PASS unknown and contradictory AA states fail closed");
    }
}
