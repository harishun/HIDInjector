package com.harishun.hidinjector;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class ShortcutManagerHelper {
    private static final String PREF_NAME = "hid_shortcuts_pref";
    private static final String KEY_SHORTCUTS = "shortcuts_list";
    private final Context context;
    private final SharedPreferences prefs;

    public ShortcutManagerHelper(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public List<ShortcutItem> getShortcuts() {
        List<ShortcutItem> list = new ArrayList<>();
        String json = prefs.getString(KEY_SHORTCUTS, null);
        if (json == null) {
            // Default macro
            list.add(new ShortcutItem("1", "Password", "STRING supersecretpassword123\nENTER", "lock"));
            saveShortcuts(list);
            return list;
        }
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(ShortcutItem.fromJsonObject(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            Log.e("ShortcutManager", "Error parsing shortcuts JSON", e);
        }
        return list;
    }

    public void saveShortcuts(List<ShortcutItem> list) {
        try {
            JSONArray arr = new JSONArray();
            for (ShortcutItem item : list) {
                arr.put(item.toJsonObject());
            }
            prefs.edit().putString(KEY_SHORTCUTS, arr.toString()).apply();
        } catch (Exception e) {
            Log.e("ShortcutManager", "Error saving shortcuts JSON", e);
        }
    }

    public int getIconResourceId(String iconName) {
        if (iconName == null) return R.drawable.ic_shortcut_bolt;
        switch (iconName) {
            case "lock": return R.drawable.ic_shortcut_lock;
            case "terminal": return R.drawable.ic_shortcut_terminal;
            case "key": return R.drawable.ic_shortcut_key;
            case "web": return R.drawable.ic_shortcut_web;
            case "keyboard": return R.drawable.ic_shortcut_keyboard;
            case "bolt":
            default:
                return R.drawable.ic_shortcut_bolt;
        }
    }

    @SuppressLint("NewApi")
    public boolean pinShortcutToHomeScreen(ShortcutItem item) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        ShortcutManager sm = context.getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            return false;
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction("com.harishun.hidinjector.ACTION_EXECUTE_SHORTCUT");
        intent.putExtra("extra_script", item.script);
        intent.putExtra("extra_name", item.name);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int iconRes = getIconResourceId(item.iconName);

        ShortcutInfo pinShortcutInfo = new ShortcutInfo.Builder(context, "shortcut_" + item.id)
                .setShortLabel(item.name)
                .setLongLabel(item.name)
                .setIcon(Icon.createWithResource(context, iconRes))
                .setIntent(intent)
                .build();

        Intent pinnedShortcutCallbackIntent = sm.createShortcutResultIntent(pinShortcutInfo);
        PendingIntent successPendingIntent = PendingIntent.getBroadcast(context, 0,
                pinnedShortcutCallbackIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return sm.requestPinShortcut(pinShortcutInfo, successPendingIntent.getIntentSender());
    }
}
