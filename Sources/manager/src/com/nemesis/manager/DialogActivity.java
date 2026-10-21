package com.nemesis.manager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;

public class DialogActivity extends Activity {
    private String mode;
    private String sessionDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Ui.setLayout(this, R.layout.dialog_alert)) {
            Ui.fallbackDialog(this);
        }
        bind(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        bind(intent);
    }

    private void bind(Intent intent) {
        mode = intent.getStringExtra("nemesis_mode");
        if (mode == null) mode = "invalid";
        sessionDir = intent.getStringExtra("session_dir");
        if (sessionDir == null) sessionDir = "/data/local/tmp/nemesis/usb-session";
        String name = intent.getStringExtra("nemesis_name");
        if (findViewById(R.id.alertTitle) == null || findViewById(R.id.button1) == null) {
            Ui.fallbackDialog(this);
        }
        final TextView title = (TextView) findViewById(R.id.alertTitle);
        final TextView msg = (TextView) findViewById(R.id.message);
        final Button yes = (Button) findViewById(R.id.button1);
        final Button no = (Button) findViewById(R.id.button2);
        if (title == null || msg == null || yes == null || no == null) return;
        title.setText(getString(R.string.title));
        boolean yn = false;
        if ("confirm_execute".equals(mode)) {
            msg.setText(R.string.confirm_execute);
            yn = true;
        } else if ("confirm_install".equals(mode)) {
            msg.setText(R.string.confirm_install);
            yn = true;
        } else if ("confirm_open".equals(mode)) {
            msg.setText(R.string.confirm_open);
            yn = true;
        } else if ("update".equals(mode)) {
            String n = name != null ? name : "";
            msg.setText("Package \"" + n + "\" is already installed. Do you wish to update it?");
            yn = true;
        } else if ("wipe".equals(mode)) {
            msg.setText(R.string.wipe_q);
            yn = true;
        } else if ("invalid".equals(mode)) {
            msg.setText(R.string.invalid);
        } else if ("forbidden".equals(mode)) {
            msg.setText(R.string.forbidden);
        } else if ("result_ok".equals(mode)) {
            msg.setText(R.string.result_ok);
        } else {
            msg.setText(R.string.result_fail);
        }
        if (yn) {
            yes.setText(R.string.yes);
            no.setText(R.string.no);
            yes.setVisibility(View.VISIBLE);
            yes.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    onYes();
                }
            });
            no.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    writeReply("no");
                    finish();
                }
            });
        } else {
            yes.setVisibility(View.GONE);
            no.setText(R.string.ok);
            no.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    writeReply("ok");
                    finish();
                }
            });
        }
    }

    private void onYes() {
        if ("wipe".equals(mode)) {
            findViewById(R.id.button1).setEnabled(false);
            findViewById(R.id.button2).setEnabled(false);
            new Thread(new Runnable() {
                public void run() {
                    Cli.runRc("wipe", "--confirm");
                    runOnUiThread(new Runnable() {
                        public void run() {
                            writeReply("yes");
                            finish();
                        }
                    });
                }
            }).start();
            return;
        }
        writeReply("yes");
        if ("confirm_open".equals(mode)) {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        }
        finish();
    }

    private void writeReply(String value) {
        File dir = new File(sessionDir);
        dir.mkdirs();
        dir.setReadable(true, false);
        dir.setWritable(true, false);
        dir.setExecutable(true, false);
        writeOne(new File(dir, "reply"), value);
        writeOne(new File("/data/nemesis/usb-session/reply"), value);
    }

    private void writeOne(File f, String value) {
        try {
            File parent = f.getParentFile();
            if (parent != null) {
                parent.mkdirs();
                parent.setWritable(true, false);
                parent.setExecutable(true, false);
            }
            FileOutputStream fos = new FileOutputStream(f);
            fos.write((value + "\n").getBytes("UTF-8"));
            fos.close();
            f.setReadable(true, false);
            f.setWritable(true, false);
        } catch (Exception ignored) {
        }
    }
}
