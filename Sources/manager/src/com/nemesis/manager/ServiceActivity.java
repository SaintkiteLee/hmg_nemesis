package com.nemesis.manager;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

public class ServiceActivity extends Activity {
    private final ArrayList<TwoLineAdapter.Row> rows = new ArrayList<TwoLineAdapter.Row>();
    private int selected = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.ensureWatchdog();
        if (!Ui.setLayout(this, R.layout.activity_list)) {
            Ui.fallbackShell(this, true, true, false);
        }
        Ui.bindTitle(this, R.string.services);
        TextView empty = (TextView) findViewById(R.id.empty);
        if (empty != null) empty.setText(R.string.empty_services);
        ListView lv = (ListView) findViewById(R.id.list);
        if (lv == null) return;
        lv.setEmptyView(findViewById(R.id.empty));
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                selected = pos;
            }
        });
        Button a = (Button) findViewById(R.id.btn_a);
        Button b = (Button) findViewById(R.id.btn_b);
        if (a == null || b == null) return;
        a.setText(R.string.start);
        b.setText(R.string.stop);
        a.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                runOp("start");
            }
        });
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                runOp("stop");
            }
        });
        reload();
    }

    private void runOp(final String op) {
        if (selected < 0 || selected >= rows.size()) {
            Ui.toast(this, R.string.select_item);
            return;
        }
        final String name = rows.get(selected).key;
        if (name == null || name.length() == 0) return;
        new Thread(new Runnable() {
            public void run() {
                Cli.runRc("service", op, name);
                runOnUiThread(new Runnable() {
                    public void run() {
                        reload();
                    }
                });
            }
        }).start();
    }

    private void reload() {
        selected = -1;
        new Thread(new Runnable() {
            public void run() {
                final ArrayList<TwoLineAdapter.Row> next = parse(Cli.run("service", "list"));
                runOnUiThread(new Runnable() {
                    public void run() {
                        rows.clear();
                        rows.addAll(next);
                        ListView lv = (ListView) findViewById(R.id.list);
                        if (lv != null) lv.setAdapter(new TwoLineAdapter(ServiceActivity.this, rows));
                        selected = -1;
                    }
                });
            }
        }).start();
    }

    private ArrayList<TwoLineAdapter.Row> parse(String out) {
        ArrayList<TwoLineAdapter.Row> list = new ArrayList<TwoLineAdapter.Row>();
        if (out == null) out = "";
        String[] lines = out.split("\n");
        int parsed = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0) continue;
            if (line.startsWith("nemesis:") || line.indexOf("not SUID") >= 0
                    || line.indexOf("daemon not running") >= 0) {
                continue;
            }
            String[] p = line.split("\\|");
            if (p.length >= 3) {
                String qn = p[0];
                String mode = p[1];
                String st = p[2];
                String pid = p.length > 3 ? p[3] : "";
                String sum = mode + "  " + st + (pid.length() > 0 && !"0".equals(pid) ? "  pid " + pid : "");
                list.add(new TwoLineAdapter.Row(qn, sum, qn, false));
                parsed++;
            }
        }
        if (parsed == 0 && out.trim().length() > 0) {
            list.add(new TwoLineAdapter.Row(out.trim(), "", "", false));
        }
        return list;
    }
}
