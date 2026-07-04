package com.harishun.hidinjector;

import org.json.JSONException;
import org.json.JSONObject;

public class ShortcutItem {
    public String id;
    public String name;
    public String script;
    public String iconName; // "bolt", "lock", "terminal", "key", "web", "keyboard"

    public ShortcutItem(String id, String name, String script, String iconName) {
        this.id = id;
        this.name = name;
        this.script = script;
        this.iconName = iconName;
    }

    public JSONObject toJsonObject() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("name", name);
        obj.put("script", script);
        obj.put("iconName", iconName);
        return obj;
    }

    public static ShortcutItem fromJsonObject(JSONObject obj) throws JSONException {
        return new ShortcutItem(
            obj.getString("id"),
            obj.getString("name"),
            obj.getString("script"),
            obj.getString("iconName")
        );
    }
}
