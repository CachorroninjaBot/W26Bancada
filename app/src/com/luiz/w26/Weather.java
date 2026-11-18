package com.luiz.w26;

import android.content.Context;
import android.location.Geocoder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Clima via Open-Meteo (gratis, sem chave) empurrado ao relogio como X0,WEATHER;cidade;dias. */
public final class Weather {
    private Weather() {
    }

    public static void fetchAndPush(Context c) {
        if (!Finder.hasFix(c)) {
            W26.get().note("clima: sem GPS guardado; atualize o GPS primeiro");
            return;
        }
        final double lat = Finder.lat(c);
        final double lon = Finder.lon(c);
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                String city = city(app, lat, lon);
                String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,weather_code"
                        + "&timezone=auto&forecast_days=6";
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(8000);
                String body = read(con.getInputStream());
                con.disconnect();
                JSONObject d = new JSONObject(body).getJSONObject("daily");
                JSONArray time = d.getJSONArray("time");
                JSONArray max = d.getJSONArray("temperature_2m_max");
                JSONArray min = d.getJSONArray("temperature_2m_min");
                JSONArray code = d.getJSONArray("weather_code");
                StringBuilder sb = new StringBuilder();
                int n = Math.min(6, time.length());
                for (int i = 0; i < n; i++) {
                    if (i > 0) sb.append('|');
                    String day = time.getString(i);
                    int wmo = code.optInt(i, 0);
                    sb.append(weekOf(day)).append(',').append(day).append(',')
                            .append((int) Math.round(min.getDouble(i))).append(',')
                            .append((int) Math.round(max.getDouble(i))).append(',')
                            .append(icon(wmo)).append(',').append(desc(wmo));
                }
                W26.get().sendTjd("X0,WEATHER;" + city + ";" + sb);
                W26.get().note("clima enviado: " + city + " (" + n + " dias)");
            } catch (Exception e) {
                W26.get().note("clima falhou: " + e.getMessage());
            }
        }, "weather").start();
    }

    private static String city(Context c, double lat, double lon) {
        try {
            if (Geocoder.isPresent()) {
                java.util.List<android.location.Address> a =
                        new Geocoder(c, Locale.getDefault()).getFromLocation(lat, lon, 1);
                if (a != null && !a.isEmpty()) {
                    String l = a.get(0).getLocality();
                    if (l != null && !l.isEmpty()) return l.replace(';', ' ');
                }
            }
        } catch (Exception ignored) {
        }
        return "Local";
    }

    private static int weekOf(String yyyyMMdd) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(yyyyMMdd);
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            return c.get(Calendar.DAY_OF_WEEK) - 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int icon(int wmo) {
        if (wmo == 0) return 0;
        if (wmo <= 3) return 1;
        if (wmo == 45 || wmo == 48) return 2;
        if (wmo >= 71 && wmo <= 77) return 4;
        if (wmo >= 95) return 5;
        return 3;
    }

    private static String desc(int wmo) {
        if (wmo == 0) return "Ceu limpo";
        if (wmo <= 3) return "Nublado";
        if (wmo == 45 || wmo == 48) return "Nevoeiro";
        if (wmo >= 71 && wmo <= 77) return "Neve";
        if (wmo >= 95) return "Tempestade";
        return "Chuva";
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int r;
        while ((r = in.read(b)) > 0) o.write(b, 0, r);
        in.close();
        return new String(o.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }
}