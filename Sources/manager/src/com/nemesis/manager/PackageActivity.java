package com.nemesis.manager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

public class PackageActivity extends Activity {
    private final ArrayList<TwoLineAdapter.Row> rows = new ArrayList<TwoLineAdapter.Row>();
    private TwoLineAdapter adapter;
    private int selected = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.ensureWatchdog();
        if (!Ui.setLayout(this, R.layout.activity_list)) {
            Ui.fallbackShell(this, true, true, false);
        }
        Ui.bindTitle(this, R.string.packages);
        TextView empty = (TextView) findViewById(R.id.empty);
        if (empty != null) empty.setText(R.string.empty_packages);
        ListView lv = (ListView) findViewById(R.id.list);
        if (lv == null) return;
        lv.setEmptyView(findViewById(R.id.empty));
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                selected = pos;
                if (adapter != null) adapter.setSelected(pos);
            }
        });
        Button a = (Button) findViewById(R.id.btn_a);
        Button b = (Button) findViewById(R.id.btn_b);
        if (a == null || b == null) return;
        a.setText(R.string.launch);
        b.setText(R.string.uninstall);
        a.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selected < 0 || selected >= rows.size()) {
                    Ui.toast(PackageActivity.this, R.string.select_item);
                    return;
                }
                final String id = rows.get(selected).key;
                new Thread(new Runnable() {
                    public void run() {
                        final Cli.Result r = Cli.exec("pkg-launch", id);
                        runOnUiThread(new Runnable() {
                            public void run() {
                                if (r.rc != 0) Ui.toast(PackageActivity.this, r.out.trim());
                                else Ui.toast(PackageActivity.this, r.out.trim());
                            }
                        });
                    }
                }).start();
            }
        });
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selected < 0 || selected >= rows.size()) {
                    Ui.toast(PackageActivity.this, R.string.select_item);
                    return;
                }
                TwoLineAdapter.Row row = rows.get(selected);
                if (row.locked) {
                    Intent i = new Intent(PackageActivity.this, DialogActivity.class);
                    i.putExtra("nemesis_mode", "forbidden");
                    startActivity(i);
                    return;
                }
                Intent ask = new Intent(PackageActivity.this, DialogActivity.class);
                ask.putExtra("nemesis_mode", "confirm_uninstall");
                ask.putExtra("nemesis_id", row.key);
                ask.putExtra("nemesis_message",
                        getString(R.string.confirm_uninstall).replace("%s", row.title));
                startActivity(ask);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        selected = -1;
        new Thread(new Runnable() {
            public void run() {
                final ArrayList<TwoLineAdapter.Row> next = parse(Cli.run("pkg-list"));
                runOnUiThread(new Runnable() {
                    public void run() {
                        rows.clear();
                        rows.addAll(next);
                        selected = -1;
                        adapter = new TwoLineAdapter(PackageActivity.this, rows);
                        ListView lv = (ListView) findViewById(R.id.list);
                        if (lv != null) lv.setAdapter(adapter);
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
                boolean lk = "1".equals(p[2]);
                String name = p[1].length() > 0 ? p[1] : p[0];
                String types = p.length > 3 ? p[3] : "";
                String sum = p[0] + (types.length() > 0 ? "  " + types : "")
                        + (lk ? "  [" + getString(R.string.locked_tag) + "]" : "");
                list.add(new TwoLineAdapter.Row(name, sum, p[0], lk));
                parsed++;
            }
        }
        if (parsed == 0 && out.trim().length() > 0) {
            list.add(new TwoLineAdapter.Row(out.trim(), "", "", false));
        }
        return list;
    }
}
