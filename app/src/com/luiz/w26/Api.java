package com.luiz.w26;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** API interna de controle/diagnostico do relogio, so em 127.0.0.1:8765.
 *  Do PC: adb forward tcp:8765 tcp:8765 e abrir http://127.0.0.1:8765/
 *  Tempo real: GET /events?since=<seq> devolve as linhas novas do log. */
public final class Api {
    public static final int PORT = 8765;

    private static final Api INSTANCE = new Api();

    public static Api get() {
        return INSTANCE;
    }

    private ServerSocket server;
    private Thread thread;
    private volatile boolean running;
    private Context ctx;

    private Api() {
    }

    public boolean isRunning() {
        return running;
    }

    public synchronized void start(Context c) {
        if (running) return;
        ctx = c.getApplicationContext();
        LogStore.init(ctx);
        try {
            server = new ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1"));
            running = true;
            thread = new Thread(this::loop, "w26-api");
            thread.setDaemon(true);
            thread.start();
            W26.get().note("API interna em http://127.0.0.1:" + PORT + "/");
        } catch (Exception e) {
            running = false;
            W26.get().note("API nao subiu: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        running = false;
        if (server != null) try { server.close(); } catch (Exception ignored) { }
        server = null;
    }

    private void loop() {
        while (running) {
            try {
                Socket s = server.accept();
                new Thread(() -> handle(s), "w26-api-req").start();
            } catch (Exception e) {
                if (running) W26.get().note("API accept: " + e.getMessage());
                break;
            }
        }
        running = false;
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(8000);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            String req = in.readLine();
            if (req == null) { s.close(); return; }
            String[] parts = req.split(" ");
            if (parts.length < 2) { s.close(); return; }
            String method = parts[0];
            String path = parts[1];
            int len = 0;
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                int c = line.indexOf(':');
                if (c > 0 && line.substring(0, c).trim().equalsIgnoreCase("Content-Length"))
                    len = Integer.parseInt(line.substring(c + 1).trim());
            }
            byte[] body = new byte[0];
            if (len > 0 && len <= 4 * 1024 * 1024) {
                body = new byte[len];
                int off = 0;
                InputStream raw = s.getInputStream();
                while (off < len) {
                    int n = raw.read(body, off, len - off);
                    if (n < 0) break;
                    off += n;
                }
            }
            String query = "";
            int q = path.indexOf('?');
            if (q >= 0) { query = path.substring(q + 1); path = path.substring(0, q); }
            Map<String, String> params = parseQuery(query);
            String bodyText = new String(body, StandardCharsets.UTF_8);
            if (!bodyText.isEmpty() && bodyText.trim().startsWith("{")) {
                try {
                    JSONObject j = new JSONObject(bodyText);
                    for (java.util.Iterator<String> it = j.keys(); it.hasNext(); ) {
                        String k = it.next();
                        if (!params.containsKey(k)) params.put(k, j.optString(k));
                    }
                } catch (Exception ignored) {
                }
            }
            Response r = route(method, path, params, body);
            OutputStream out = s.getOutputStream();
            byte[] payload = r.body.getBytes(StandardCharsets.UTF_8);
            String head = "HTTP/1.1 " + r.status + "\r\n"
                    + "Content-Type: " + r.type + "; charset=utf-8\r\n"
                    + "Content-Length: " + payload.length + "\r\n"
                    + "Access-Control-Allow-Origin: *\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.UTF_8));
            out.write(payload);
            out.flush();
            s.close();
        } catch (Exception ignored) {
            try { s.close(); } catch (Exception ignored2) { }
        }
    }

    private static final class Response {
        final String status, type, body;

        Response(String status, String type, String body) {
            this.status = status;
            this.type = type;
            this.body = body;
        }
    }

    private static Response json(Object o) {
        return new Response("200 OK", "application/json", o.toString());
    }

    private static Response ok(String msg) {
        JSONObject j = new JSONObject();
        try { j.put("ok", true).put("msg", msg); } catch (Exception ignored) { }
        return json(j);
    }

    private static Response err(String msg) {
        JSONObject j = new JSONObject();
        try { j.put("ok", false).put("error", msg); } catch (Exception ignored) { }
        return new Response("400 Bad Request", "application/json", j.toString());
    }

    private Response route(String method, String path, Map<String, String> p, byte[] body) {
        try {
            W26 w = W26.get();
            if (path.equals("/")) {
                JSONObject j = new JSONObject();
                j.put("app", "W26 Bancada API");
                j.put("endpoints", new JSONArray(new String[]{
                        "GET /state", "GET /events?since=<seq>", "GET /logs/dates",
                        "GET /logs?date=yyyy-MM-dd", "GET /health?days=7", "GET /apps",
                        "POST /tjd?cmd=X0,GET,7", "POST /mtk?text=SYNC REQV", "POST /mtk?hex=f0f0...",
                        "POST /sync", "POST /battery", "POST /alert?level=2", "POST /weather",
                        "POST /connect", "POST /disconnect",
                        "POST /mree/list", "POST /mree/status?name=", "POST /mree/uninstall?name=",
                        "POST /mree/install?name=app.vxp (body=vxp)", "POST /mree/uninstall-all"}));
                return json(j);
            }
            if (path.equals("/state")) {
                JSONObject j = new JSONObject();
                j.put("ble", w.isConnected()).put("spp", w.isSppUp())
                        .put("state", w.getState()).put("battery", w.getWatchBattery())
                        .put("wantConn", Prefs.wantConn(ctx)).put("autoConnect", Prefs.autoConnect(ctx))
                        .put("lastSync", Prefs.lastSync(ctx)).put("logSeq", LogStore.lastSeq());
                JSONObject info = new JSONObject();
                for (Map.Entry<String, String> e : w.info.entrySet()) info.put(e.getKey(), e.getValue());
                j.put("info", info);
                j.put("health", Health.get().summaryJson());
                j.put("apps", Apps.get().toJson());
                return json(j);
            }
            if (path.equals("/events")) {
                long since = Long.parseLong(p.getOrDefault("since", "0"));
                List<LogStore.Line> lines = LogStore.since(since);
                JSONArray arr = new JSONArray();
                for (LogStore.Line l : lines) {
                    JSONObject o = new JSONObject();
                    o.put("seq", l.seq).put("ts", l.ts).put("text", l.text);
                    arr.put(o);
                }
                JSONObject j = new JSONObject();
                j.put("seq", LogStore.lastSeq()).put("lines", arr);
                return json(j);
            }
            if (path.equals("/logs/dates")) return json(new JSONArray(LogStore.dates()));
            if (path.equals("/logs")) {
                String day = p.getOrDefault("date", LogStore.today());
                return new Response("200 OK", "text/plain", LogStore.read(day));
            }
            if (path.equals("/health")) {
                int days = Integer.parseInt(p.getOrDefault("days", "7"));
                return json(Health.get().historyJson(days));
            }
            if (path.equals("/apps")) return json(Apps.get().toJson());
            if (path.equals("/tjd")) {
                String cmd = p.get("cmd");
                if (cmd == null || cmd.isEmpty()) return err("cmd obrigatorio");
                w.sendTjd(cmd);
                return ok("TJD enviado: " + cmd);
            }
            if (path.equals("/mtk")) {
                if (p.containsKey("hex")) {
                    String h = p.get("hex").replaceAll("\\s", "");
                    byte[] b = new byte[h.length() / 2];
                    for (int i = 0; i < b.length; i++)
                        b[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
                    w.sendMtkAuto(b);
                    return ok("MTK hex enviado (" + b.length + " B)");
                }
                String text = p.get("text");
                if (text == null || text.isEmpty()) return err("text ou hex obrigatorio");
                w.sendMtkAuto(Mtk.cmd(text));
                return ok("MTK enviado: " + text);
            }
            if (path.equals("/sync")) {
                w.syncTime();
                w.battery();
                Prefs.markSync(ctx);
                Health.get().syncAll();
                return ok("sync enviado");
            }
            if (path.equals("/battery")) { w.battery(); return ok("pedido de bateria"); }
            if (path.equals("/alert")) {
                w.alertWatch(Integer.parseInt(p.getOrDefault("level", "2")));
                return ok("alerta enviado");
            }
            if (path.equals("/weather")) { Weather.fetchAndPush(ctx); return ok("clima pedido"); }
            if (path.equals("/connect")) {
                Prefs.setWantConn(ctx, true);
                LinkSvc.start(ctx);
                return ok("conectando");
            }
            if (path.equals("/disconnect")) {
                Prefs.setWantConn(ctx, false);
                w.disconnect();
                w.disconnectSpp();
                return ok("desconectado");
            }
            if (path.equals("/mree/list")) { w.mreeList(); return ok("lista pedida"); }
            if (path.equals("/mree/status")) { w.mreeStatus(p.getOrDefault("name", "")); return ok("status pedido"); }
            if (path.equals("/mree/uninstall")) {
                String name = p.get("name");
                if (name == null || name.isEmpty()) return err("name obrigatorio");
                w.mreeUninstall(name);
                return ok("uninstall pedido: " + name);
            }
            if (path.equals("/mree/uninstall-all")) { w.mreeUninstallAll(); return ok("uninstall all pedido"); }
            if (path.equals("/mree/install")) {
                String name = p.getOrDefault("name", "app.vxp");
                if (body.length == 0) return err("body vazio (envie o .vxp cru)");
                w.mreeInstall(name, body);
                return ok("install enviado: " + name + " (" + body.length + " B)");
            }
            return new Response("404 Not Found", "application/json", "{\"ok\":false,\"error\":\"rota desconhecida\"}");
        } catch (Exception e) {
            return err(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static Map<String, String> parseQuery(String q) {
        Map<String, String> out = new HashMap<>();
        if (q == null || q.isEmpty()) return out;
        for (String kv : q.split("&")) {
            int e = kv.indexOf('=');
            try {
                if (e > 0) out.put(URLDecoder.decode(kv.substring(0, e), "UTF-8"),
                        URLDecoder.decode(kv.substring(e + 1), "UTF-8"));
                else if (!kv.isEmpty()) out.put(URLDecoder.decode(kv, "UTF-8"), "");
            } catch (Exception ignored) {
            }
        }
        return out;
    }
}