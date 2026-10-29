package com.daudio.app.deviceconnections.ui.connectivitySettings;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import com.daudio.app.deviceconnections.k5fix.SafeActivity;
public final class PhoneProjectionSettingsActivity extends SafeActivity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try{
            Intent i=new Intent();
            i.setClassName("com.daudio.app.phoneprojection","com.daudio.app.phoneprojection.wireless.WPPSettingsActivity");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        }catch(RuntimeException e){Log.e("K5AA","Stock settings could not open",e);Toast.makeText(this,"차량 연결 설정을 열 수 없습니다.",Toast.LENGTH_LONG).show();}
        finish();
    }
}
