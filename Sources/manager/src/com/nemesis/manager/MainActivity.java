package com.nemesis.manager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int ACT_USB = 0;
    private static final int ACT_PKG = 1;
    private static final int ACT_SVC = 2;
    private static final int ACT_LOG = 3;
    private static final int ACT_OPTIONS = 4;
    private static final int ACT_ENG = 5;
    private static final int ACT_DEALER = 6;
    private static final int ACT_WIPE = 7;
    private static final String ENG_COMPONENT =
            "com.hkmc.system.app.engineering/.EngineeringModeMainActivity";
    private static final String DEALER_COMPONENT =
            "com.hkmc.system.app.engineering/.DealerModeActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.ensureWatchdog();
        try {
            if (!Ui.setLayout(this, R.layout.activity_main)) {
                Ui.fallbackShell(this, false, false, false);
            }
            Ui.bindTitle(this, R.string.title);

            ListView lv = (ListView) findViewById(R.id.list);
            if (lv == null) {
                Ui.fallbackShell(this, false, false, false);
                Ui.bindTitle(this, R.string.title);
                lv = (ListView) findViewById(R.id.list);
            }
            if (lv == null) return;
            lv.setAdapter(new TwoLineAdapter(this, menuRows(true)));
            lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                    onMenu(pos);
                }
            });
        } catch (Throwable t) {
            try {
                Ui.fallbackShell(this, false, false, false);
                Ui.bindTitle(this, R.string.title);
                ListView lv = (ListView) findViewById(R.id.list);
                if (lv == null) return;
                lv.setAdapter(new TwoLineAdapter(this, menuRows(false)));
                lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                        onMenu(pos);
                    }
                });
            } catch (Throwable ignored) {
            }
        }
    }

    private void onMenu(int pos) {
        if (pos == ACT_USB) {
            new Thread(new Runnable() {
                public void run() {
                    final int rc = Cli.runRc("root", "-c",
                            "/system/bin/sh /storage/usb0/NEMESIS.sh");
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Intent i = new Intent(MainActivity.this, DialogActivity.class);
                            i.putExtra("nemesis_mode", rc == 0 ? "result_ok" : "result_fail");
                            startActivity(i);
                        }
                    });
                }
            }).start();
            return;
        }
        if (pos == ACT_PKG) {
            startActivity(new Intent(this, PackageActivity.class));
            return;
        }
        if (pos == ACT_SVC) {
            startActivity(new Intent(this, ServiceActivity.class));
            return;
        }
        if (pos == ACT_LOG) {
            startActivity(new Intent(this, LogActivity.class));
            return;
        }
        if (pos == ACT_OPTIONS) {
            startActivity(new Intent(this, OptionsActivity.class));
            return;
        }
        if (pos == ACT_ENG) {
            openComponent(ENG_COMPONENT);
            return;
        }
        if (pos == ACT_DEALER) {
            openComponent(DEALER_COMPONENT);
            return;
        }
        if (pos == ACT_WIPE) {
            Intent i = new Intent(this, DialogActivity.class);
            i.putExtra("nemesis_mode", "wipe");
            startActivity(i);
        }
    }

    private ArrayList<TwoLineAdapter.Row> menuRows(boolean summary) {
        ArrayList<TwoLineAdapter.Row> rows = new ArrayList<TwoLineAdapter.Row>();
        rows.add(new TwoLineAdapter.Row(getString(R.string.run_usb),
                summary ? "Execute NEMESIS.sh on the USB drive" : "", "usb", false));
        rows.add(new TwoLineAdapter.Row(getString(R.string.packages),
                summary ? "Installed packages" : "", "pkg", false));
        rows.add(new TwoLineAdapter.Row(getString(R.string.services),
                summary ? "Registered services" : "", "svc", false));
        rows.add(new TwoLineAdapter.Row(getString(R.string.logs),
                summary ? "NEMESIS log and copy to USB" : "", "log", false));
        rows.add(new TwoLineAdapter.Row(getString(R.string.options),
                summary ? getString(R.string.options_sum) : "", "options", false));
        rows.add(new TwoLineAdapter.Row(getString(R.string.engineering),
                summary ? "Engineering Mode" : "", "eng", false));
        rows.add(new TwoLineAdapter.Row(getString(R.string.dealer),
                summary ? "Dealer Mode" : "", "dealer", false));
        rows.add(new TwoLineAdapter.Row(getString(R.string.wipe),
                summary ? "Remove NEMESIS and all packages" : "", "wipe", false));
        return rows;
    }

    private void openComponent(final String component) {
        new Thread(new Runnable() {
            public void run() {
                final int rc = Cli.runRc("root", "-c", "am start -n " + component);
                if (rc == 0) return;
                runOnUiThread(new Runnable() {
                    public void run() {
                        Ui.toast(MainActivity.this, R.string.open_fail);
                    }
                });
            }
        }).start();
    }
}
