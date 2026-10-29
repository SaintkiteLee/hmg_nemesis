package com.daudio.app.deviceconnections.k5fix;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.ScrollView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public final class TrialResultActivity extends SafeActivity {
    private TextView status;
    private Button keep,restore;
    private RadioGroup observations;
    static String root(String command) throws Exception {
        Process p=new ProcessBuilder("/system/xbin/nemesis","root","-c",command).redirectErrorStream(true).start();
        ByteArrayOutputStream out=new ByteArrayOutputStream();InputStream in=p.getInputStream();
        try{byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}finally{in.close();}
        if(p.waitFor()!=0)throw new Exception("Command failed");return new String(out.toByteArray(),"UTF-8").trim();
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(36,28,36,28);
        status=new TextView(this);status.setTextSize(21);
        status.setText("무선 AA 화면이 실제로 열렸을 때만 설정을 유지하세요.\n실패했다면 휴대폰에서 확인한 상태를 선택하세요. 8분 후 자동 복원됩니다.");
        box.addView(status);
        observations=new RadioGroup(this);
        String[] labels={"Wi-Fi 목록 확인 안 함","차량 Wi-Fi 이름이 안 보임","이름은 보임 · 연결 안 됨","차량 Wi-Fi에 연결됨"};
        for(int i=0;i<labels.length;i++){RadioButton radio=new RadioButton(this);radio.setId(100+i);radio.setText(labels[i]);radio.setTextSize(18);observations.addView(radio);}
        observations.check(100);box.addView(observations);
        keep=new Button(this);keep.setText("AA 연결 성공 · 설정 유지");box.addView(keep);
        restore=new Button(this);restore.setText("연결 실패 · 원래 상태 복원");box.addView(restore);
        keep.setOnClickListener(new View.OnClickListener(){public void onClick(View v){decide(true);}});
        restore.setOnClickListener(new View.OnClickListener(){public void onClick(View v){decide(false);}});
        ScrollView scroll=new ScrollView(this);scroll.addView(box);setContentView(scroll);
    }
    private void decide(final boolean success){
        final String[] values={"not_checked","ap_not_visible","ap_visible_not_connected","wifi_connected"};
        int selected=observations.getCheckedRadioButtonId()-100;
        final String observation=values[selected>=0 && selected<values.length?selected:0];
        keep.setEnabled(false);restore.setEnabled(false);status.setText("처리 중입니다. USB를 연결한 채 작업 완료 표시를 기다리세요.");
        new Thread(new Runnable(){public void run(){
            try{
                String phase=root("cat /data/nemesis/k5-aa-apalign/phase");
                if(!"testing".equals(phase))throw new Exception("시험이 이미 종료됐습니다. USB 로그를 확인하세요.");
                root("echo "+observation+" > /data/nemesis/k5-aa-apalign/phone-observation.txt");
                root(success?"echo keep > /data/nemesis/k5-aa-apalign/decision":"echo rollback > /data/nemesis/k5-aa-apalign/decision");
            }catch(final Exception e){runOnUiThread(new Runnable(){public void run(){status.setText("결과를 저장하지 못했습니다. "+e.getMessage()+"\n성공 확인이 없으면 자동 복원됩니다.");}});}
        }}).start();
    }
}
