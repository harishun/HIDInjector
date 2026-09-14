package com.harishun.hidinjector;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Log;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
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
            case "rocket": return R.drawable.ic_shortcut_rocket;
            case "folder": return R.drawable.ic_shortcut_folder;
            case "shield": return R.drawable.ic_shortcut_shield;
            case "power": return R.drawable.ic_shortcut_power;
            case "media": return R.drawable.ic_shortcut_media;
            case "clipboard": return R.drawable.ic_shortcut_clipboard;
            case "laptop": return R.drawable.ic_shortcut_laptop;
            case "code": return R.drawable.ic_shortcut_code;
            case "bolt":
            default:
                return R.drawable.ic_shortcut_bolt;
        }
    }

    public Bitmap createShortcutIconBitmap(String iconName) {
        int size = 192;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 1. App Gradient Background (Squircle)
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        LinearGradient gradient = new LinearGradient(
                0, 0, size, size,
                new int[]{0xFF1E3A8A, 0xFF7E22CE},
                null,
                Shader.TileMode.CLAMP
        );
        bgPaint.setShader(gradient);
        RectF rect = new RectF(6, 6, size - 6, size - 6);
        canvas.drawRoundRect(rect, 44, 44, bgPaint);

        // 2. Purple glowing border
        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(4.5f);
        strokePaint.setColor(0xFFA855F7);
        canvas.drawRoundRect(rect, 44, 44, strokePaint);

        // 3. Center Shortcut Icon
        int iconRes = getIconResourceId(iconName);
        Drawable shortcutDrawable = ContextCompat.getDrawable(context, iconRes);
        if (shortcutDrawable != null) {
            Drawable wrapped = DrawableCompat.wrap(shortcutDrawable.mutate());
            DrawableCompat.setTint(wrapped, Color.WHITE);
            int padding = 42;
            wrapped.setBounds(padding, padding, size - padding, size - padding);
            wrapped.draw(canvas);
        }

        // 4. App Badge (Bluetooth) in bottom-right corner
        Drawable appBadge = ContextCompat.getDrawable(context, R.drawable.ic_bluetooth);
        if (appBadge != null) {
            int badgeSize = 58;
            int margin = 8;
            int left = size - badgeSize - margin;
            int top = size - badgeSize - margin;

            Paint badgeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            badgeBgPaint.setColor(0xFF0F172A);
            canvas.drawCircle(left + badgeSize / 2f, top + badgeSize / 2f, badgeSize / 2f, badgeBgPaint);

            Paint badgeStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            badgeStroke.setStyle(Paint.Style.STROKE);
            badgeStroke.setStrokeWidth(2.5f);
            badgeStroke.setColor(0xFF38BDF8);
            canvas.drawCircle(left + badgeSize / 2f, top + badgeSize / 2f, badgeSize / 2f, badgeStroke);

            Drawable wrappedBadge = DrawableCompat.wrap(appBadge.mutate());
            DrawableCompat.setTint(wrappedBadge, 0xFF38BDF8);
            int inset = 12;
            wrappedBadge.setBounds(left + inset, top + inset, left + badgeSize - inset, top + badgeSize - inset);
            wrappedBadge.draw(canvas);
        }

        return bitmap;
    }

    public boolean pinShortcutToHomeScreen(ShortcutItem item) {
        ShortcutManager sm = context.getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            return false;
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction("com.harishun.hidinjector.ACTION_EXECUTE_SHORTCUT");
        intent.putExtra("extra_script", item.script);
        intent.putExtra("extra_name", item.name);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        Bitmap iconBitmap = createShortcutIconBitmap(item.iconName);

        ShortcutInfo pinShortcutInfo = new ShortcutInfo.Builder(context, "shortcut_" + item.id)
                .setShortLabel(item.name)
                .setLongLabel(item.name)
                .setIcon(Icon.createWithBitmap(iconBitmap))
                .setIntent(intent)
                .build();

        Intent pinnedShortcutCallbackIntent = sm.createShortcutResultIntent(pinShortcutInfo);
        PendingIntent successPendingIntent = PendingIntent.getBroadcast(context, 0,
                pinnedShortcutCallbackIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return sm.requestPinShortcut(pinShortcutInfo, successPendingIntent.getIntentSender());
    }
}
