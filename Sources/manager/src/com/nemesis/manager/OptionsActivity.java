package com.nemesis.manager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.ArrayList;

/** Options list, and the trigger-button picker opened from it. */
public class OptionsActivity extends Activity {
    private boolean picking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        picking = getIntent().getBooleanExtra("pick_trigger", false);
        if (!Ui.setLayout(this, R.layout.activity_list)) {
            Ui.fallbackShell(this, true, false, false);
        }
        Ui.bindTitle(this, picking ? R.string.trigger_button : R.string.options);
        View bar = findViewById(R.id.btn_bar);
        if (bar != null) bar.setVisibility(View.GONE);
        View empty = findViewById(R.id.empty);
        if (empty != null) empty.setVisibility(View.GONE);
        bindList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!picking) bindList();
    }

    private void bindList() {
        ListView lv = (ListView) findViewById(R.id.list);
        if (lv == null) return;
        final ArrayList<TwoLineAdapter.Row> rows = new ArrayList<TwoLineAdapter.Row>();
        if (!picking) {
            rows.add(new TwoLineAdapter.Row(getString(R.string.trigger_button),
                    Trigger.label(this) + ". " + getString(R.string.trigger_hint),
                    "trigger", false));
        } else {
            String current = Trigger.suffix(this);
            for (int i = 0; i < Trigger.ALL.length; i++) {
                Trigger.Item item = Trigger.ALL[i];
                String sum = item.suffix.equals(current) ? getString(R.string.selected) : "";
                rows.add(new TwoLineAdapter.Row(item.label, sum, item.suffix, false));
            }
        }
        lv.setAdapter(new TwoLineAdapter(this, rows));
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos < 0 || pos >= rows.size()) return;
                if (!picking) {
                    Intent i = new Intent(OptionsActivity.this, OptionsActivity.class);
                    i.putExtra("pick_trigger", true);
                    startActivity(i);
                    return;
                }
                try {
                    Trigger.save(OptionsActivity.this, rows.get(pos).key);
                } catch (Throwable ignored) {
                }
                finish();
            }
        });
    }
}
