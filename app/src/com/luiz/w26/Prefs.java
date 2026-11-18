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

    public static java.util.Set<String> notifBlocked(Context c) {
        return new java.util.HashSet<>(p(c).getStringSet("notifBlocked", new java.util.HashSet<>()));
    }

    public static void setNotifBlocked(Context c, String pkg, boolean blocked) {
        java.util.Set<String> s = notifBlocked(c);
        if (blocked) s.add(pkg); else s.remove(pkg);
        p(c).edit().putStringSet("notifBlocked", s).apply();
    }

    public static boolean notifSkipSystem(Context c) {
        return p(c).getBoolean("notifSkipSystem", true);
    }

    public static void setNotifSkipSystem(Context c, boolean v) {
        p(c).edit().putBoolean("notifSkipSystem", v).apply();
    }
}