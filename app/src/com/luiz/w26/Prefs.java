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

    /** Reconectar sozinho quando o relogio aparece / bluetooth liga / app abre. */
    public static boolean autoConnect(Context c) {
        return p(c).getBoolean("autoConnect", true);
    }

    public static void setAutoConnect(Context c, boolean v) {
        p(c).edit().putBoolean("autoConnect", v).apply();
    }

    /** Intencao do usuario: false depois de "Desconectar" manual (nao reconecta sozinho). */
    public static boolean wantConn(Context c) {
        return p(c).getBoolean("wantConn", true);
    }

    public static void setWantConn(Context c, boolean v) {
        p(c).edit().putBoolean("wantConn", v).apply();
    }

    public static String bleMac(Context c) {
        return p(c).getString("bleMac", "C2:BA:45:15:C8:F9");
    }

    public static String sppMac(Context c) {
        return p(c).getString("sppMac", "06:87:E6:0C:62:61");
    }

    public static void setMacs(Context c, String ble, String spp) {
        p(c).edit().putString("bleMac", ble).putString("sppMac", spp).apply();
    }

    /** API HTTP interna de diagnostico/controle (so localhost). */
    public static boolean apiEnabled(Context c) {
        return p(c).getBoolean("apiEnabled", true);
    }

    public static void setApiEnabled(Context c, boolean v) {
        p(c).edit().putBoolean("apiEnabled", v).apply();
    }

    /** Sincronizar saude (passos/BPM/sono) ao conectar e periodicamente. */
    public static boolean healthAuto(Context c) {
        return p(c).getBoolean("healthAuto", true);
    }

    public static void setHealthAuto(Context c, boolean v) {
        p(c).edit().putBoolean("healthAuto", v).apply();
    }
}