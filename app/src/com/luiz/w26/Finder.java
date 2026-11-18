package com.luiz.w26;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Finder: o relogio nao tem GPS. Guarda a ultima posicao do CELULAR quando conecta
 *  e mostra num mapa; o alerta no relogio (1802/2A06 + TJD) ajuda a achar por perto. */
public final class Finder {
    private static final String PREF = "finder";

    private Finder() {
    }

    public static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static void save(Context c, Location loc) {
        if (loc == null) return;
        prefs(c).edit()
                .putLong("lat", Double.doubleToRawLongBits(loc.getLatitude()))
                .putLong("lon", Double.doubleToRawLongBits(loc.getLongitude()))
                .putLong("time", loc.getTime() > 0 ? loc.getTime() : System.currentTimeMillis())
                .putFloat("acc", loc.getAccuracy())
                .apply();
    }

    /** Pega a melhor ultima posicao conhecida e pede um fix novo (salva quando chegar). */
    public static void capture(Context c) {
        if (!hasPermission(c)) return;
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;
        Location best = null;
        for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (SecurityException | IllegalArgumentException ignored) {
            }
        }
        if (best != null) save(c, best);
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, new LocationListener() {
                @Override public void onLocationChanged(Location l) {
                    save(c, l);
                    lm.removeUpdates(this);
                }
                @Override public void onStatusChanged(String p, int s, Bundle e) { }
                @Override public void onProviderEnabled(String p) { }
                @Override public void onProviderDisabled(String p) { }
            }, Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }

    public static boolean hasFix(Context c) {
        return prefs(c).contains("lat");
    }

    public static double lat(Context c) {
        return Double.longBitsToDouble(prefs(c).getLong("lat", 0));
    }

    public static double lon(Context c) {
        return Double.longBitsToDouble(prefs(c).getLong("lon", 0));
    }

    public static String describe(Context c) {
        if (!hasFix(c)) return "Nenhuma posicao guardada ainda.\nConecte ao relogio com o GPS do celular ligado.";
        long t = prefs(c).getLong("time", 0);
        String when = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date(t));
        float acc = prefs(c).getFloat("acc", 0);
        return String.format(Locale.getDefault(),
                "Ultima conexao: %s\nLat %.6f  Lon %.6f\nPrecisao ~%.0f m", when, lat(c), lon(c), acc);
    }

    /** Abre a posicao no app de mapas do celular (geo: URI, sem chave de API). */
    public static Intent mapsIntent(Context c) {
        Uri uri = Uri.parse("geo:" + lat(c) + "," + lon(c) + "?q=" + lat(c) + "," + lon(c)
                + "(Ultima+conexao+relogio)");
        return new Intent(Intent.ACTION_VIEW, uri);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}