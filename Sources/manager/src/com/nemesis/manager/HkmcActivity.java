package com.nemesis.manager;

import android.app.Activity;
import android.util.Log;

/** Some HKMC KitKat builds check GET_TASKS even while finishing our own screen. */
public class HkmcActivity extends Activity {
    @Override public void finish() {
        try {
            super.finish();
        } catch (SecurityException error) {
            Log.e("NEMESIS", "OEM rejected activity finish", error);
            try {
                moveTaskToBack(true);
            } catch (SecurityException fallbackError) {
                Log.e("NEMESIS", "OEM rejected task background", fallbackError);
            }
        }
    }
}
