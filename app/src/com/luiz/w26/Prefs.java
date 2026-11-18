package com.luiz.w26;

import android.content.Context;
import android.content.SharedPreferences;

/** Preferencias de sincronizacao celular<->relogio. */
public final class Prefs {
    private Prefs() {
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("w26", Context.MODE_PRIVATE);
    }

    public static boolean autoSync(Context c) {
        return p(c).getBoolean("autoSync", true);
    }

    public static void setAutoSync(Context c, boolean v) {
        p(c).edit().putBoolean("autoSync", v).apply();
    }

    public static boolean notifEnabled(Context c) {
        return p(c).getBoolean("notif", true);
    }

    public static void setNotifEnabled(Context c, boolean v) {
        p(c).edit().putBoolean("notif", v).apply();
    }

    public static long lastSync(Context c) {
        return p(c).getLong("lastSync", 0);
    }

    public static void markSync(Context c) {
        p(c).edit().putLong("lastSync", System.currentTimeMillis()).apply();
    }
}