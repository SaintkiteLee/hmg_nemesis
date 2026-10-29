package com.daudio.app.deviceconnections.k5fix;
import android.app.Activity;
import android.util.Log;
public class SafeActivity extends Activity {
    @Override public void finish(){
        try{super.finish();}catch(SecurityException e){
            Log.e("K5AA","OEM finish rejected",e);
            try{moveTaskToBack(true);}catch(SecurityException x){Log.e("K5AA","OEM task close rejected",x);}
        }
    }
}
