package com.luiz.w26;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Apps/mostradores instalados no relogio (MREE cmd 5).
 *  Resposta real: "mre_installer 5 <nome,versao,flag> ..."; flag "1" = pre-instalado
 *  (nao removivel). Entries entre < >, separados por espaco. O relogio demora ~35s a responder. */
public final class Apps implements W26.Listener {
    private static final Apps INSTANCE = new Apps();

    public static Apps get() {
        return INSTANCE;
    }

    public static final class App {
        public final String name, ver;
        public final boolean preinstalled;

        App(String name, String ver, boolean preinstalled) {
            this.name = name;
            this.ver = ver;
            this.preinstalled = preinstalled;
        }
    }

    public interface Callback {
        void onAppsUpdated();
    }

    private final List<App> apps = new ArrayList<>();
    private final List<Callback> callbacks = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile String lastRaw = "";
    private volatile long lastUpdate;

    private Apps() {
    }

    public void init() {
        W26.get().addListener(this);
    }

    public void addCallback(Callback c) {
        if (c != null && !callbacks.contains(c)) callbacks.add(c);
    }

    public void removeCallback(Callback c) {
        callbacks.remove(c);
    }

    public void refresh() {
        if (!W26.get().isSppUp()) {
            W26.get().note("apps: precisa do SPP conectado");
            return;
        }
        W26.get().mreeList();
    }

    public List<App> list() {
        synchronized (apps) {
            return new ArrayList<>(apps);
        }
    }

    public int count() {
        synchronized (apps) {
            return apps.size();
        }
    }

    public long lastUpdate() {
        return lastUpdate;
    }

    @Override
    public void onLog(String line) {
    }

    @Override
    public void onState(String state) {
    }

    @Override
    public void onMreReply(String reply) {
        lastRaw = reply;
        if (!reply.startsWith("mre_installer 5")) return;
        // Formato real: "mre_installer 5 <nome,versao,flag> <nome2,versao2,flag2> ..."
        // (entries entre < >, separados por espaco; flag "1" = pre-instalado).
        String body = reply.substring("mre_installer 5".length()).trim();
        List<App> parsed = new ArrayList<>();
        for (String tok : body.split("\\s+")) {
            String t = tok.replaceAll("^[(){}\\[\\]<>]+", "").replaceAll("[(){}\\[\\]<>]+$", "");
            if (t.isEmpty()) continue;
            String[] f = t.split(",");
            if (f.length >= 3) {
                String name = f[0].trim();
                if (name.isEmpty()) continue;
                parsed.add(new App(name, f[1].trim(), f[2].trim().equals("1")));
            }
        }
        synchronized (apps) {
            apps.clear();
            apps.addAll(parsed);
        }
        lastUpdate = System.currentTimeMillis();
        W26.get().note("apps no relogio: " + parsed.size() + " (" + removable() + " removiveis)");
        for (Callback c : callbacks) c.onAppsUpdated();
    }

    public int removable() {
        synchronized (apps) {
            int n = 0;
            for (App a : apps) if (!a.preinstalled) n++;
            return n;
        }
    }

    /** Desinstala so os apps que nos instalamos (preserva os de fabrica). */
    public void freeSpace() {
        List<App> snap = list();
        int n = 0;
        for (App a : snap) {
            if (!a.preinstalled) {
                W26.get().mreeUninstall(a.name);
                n++;
            }
        }
        W26.get().note("liberar espaco: uninstall de " + n + " apps custom");
    }

    public JSONObject toJson() {
        JSONObject j = new JSONObject();
        try {
            j.put("count", count()).put("removable", removable()).put("lastUpdate", lastUpdate);
            JSONArray arr = new JSONArray();
            for (App a : list()) {
                JSONObject o = new JSONObject();
                o.put("name", a.name).put("ver", a.ver).put("preinstalled", a.preinstalled);
                arr.put(o);
            }
            j.put("apps", arr);
        } catch (Exception ignored) {
        }
        return j;
    }
}