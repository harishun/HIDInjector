package com.harishun.hidinjector;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static final String PREFS_NAME = "hid_settings";
    private static final String KEY_OS = "target_os";
    private static final String KEY_SENSITIVITY = "mouse_sensitivity";
    private static final String KEY_LAYOUT = "keyboard_layout";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getTargetOS() {
        return prefs.getString(KEY_OS, "Windows");
    }

    public void setTargetOS(String os) {
        prefs.edit().putString(KEY_OS, os).apply();
    }

    public float getSensitivity() {
        return prefs.getFloat(KEY_SENSITIVITY, 3.0f);
    }

    public void setSensitivity(float val) {
        prefs.edit().putFloat(KEY_SENSITIVITY, val).apply();
    }

    public String getKeyboardLayout() {
        return prefs.getString(KEY_LAYOUT, "US (QWERTY)");
    }

    public void setKeyboardLayout(String layout) {
        prefs.edit().putString(KEY_LAYOUT, layout).apply();
    }
}
