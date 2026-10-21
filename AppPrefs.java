package com.anthurium.soilcontroller.data;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPrefs {

    private static final String PREFS_NAME = "soil_controller_prefs";
    private static final String KEY_DEVICE_URL = "device_base_url";
    private static final String KEY_MODE = "control_mode";

    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getDeviceBaseUrl() {
        return prefs.getString(KEY_DEVICE_URL, "http://192.168.0.50"); //Dito palit
    }

    public void setDeviceBaseUrl(String url) {
        prefs.edit().putString(KEY_DEVICE_URL, url).apply();
    }

    public String getMode() {
        return prefs.getString(KEY_MODE, "MANUAL");
    }

    public void setMode(String mode) {
        prefs.edit().putString(KEY_MODE, mode).apply();
    }
}
