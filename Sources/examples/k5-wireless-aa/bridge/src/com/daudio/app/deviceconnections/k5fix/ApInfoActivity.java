package com.daudio.app.deviceconnections.k5fix;
import android.os.Bundle;
import android.widget.TextView;

/** Public AP name only; credentials remain in the root process. */
public final class ApInfoActivity extends SafeActivity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        final TextView text=new TextView(this);text.setPadding(32,22,32,22);text.setTextSize(23);
        text.setText("차량 Wi-Fi 이름을 확인 중입니다.");setContentView(text);
        new Thread(new Runnable(){public void run(){
            String message;
            try {
                String output=TrialResultActivity.root("CLASSPATH=/data/nemesis/k5-aa-apalign/guard.jar /system/bin/app_process /system/bin com.saintkite.k5aa.Main ap-info /data/nemesis/k5-aa-apalign");
                String name="확인 불가",channel="?";
                for(String line:output.split("\n")) {
                    if(line.startsWith("AP_NAME="))name=line.substring(8);
                    if(line.startsWith("AP_CHANNEL="))channel=line.substring(11);
                }
                message="차량 Wi-Fi: "+name+"\n채널: "+channel+"\n\nOperation success가 뜨면 USB를 빼고 15초 후 무선 AA를 시도하세요.\n연결되지 않으면 휴대폰 Wi-Fi 목록에 위 이름이 보이는지 확인하세요.\n8분 이내에 USB를 다시 연결해 결과를 선택하세요.";
            }catch(Exception failure){message="AP 이름을 표시하지 못했습니다.\nOperation success 후 USB를 빼고 무선 AA를 시도하세요.\n8분 이내에 USB를 다시 연결하세요.";}
            final String result=message;runOnUiThread(new Runnable(){public void run(){text.setText(result);}});
        }}).start();
    }
}
