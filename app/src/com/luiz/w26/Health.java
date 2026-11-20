package com.luiz.w26;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Saude do relogio (protocolo TJD ASCII, RE do app oficial M Activie):
 *  X0,GET,1 resumo do dia (passos/sono/BPM), 2 passos por hora, 3 segmentos de sono,
 *  4 BPM, 8 SpO2, 9 temperatura. Medicao ao vivo: X1,SET,*Open,1 -> push do relogio.
 *  Historico em SQLite (health.db), um registro por amostra. */
public final class Health implements W26.Listener {
    private static final Health INSTANCE = new Health();

    public static Health get() {
        return INSTANCE;
    }

    public interface Callback {
        void onHealthUpdated();

        default void onMeasure(String type, double value) {
        }
    }

    private Db db;
    private Context ctx;
    private final List<Callback> callbacks = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile String measuring;

    private Health() {
    }

    public void init(Context c) {
        if (db != null) return;
        ctx = c.getApplicationContext();
        db = new Db(ctx);
        W26.get().addListener(this);
    }

    public void addCallback(Callback c) {
        if (c != null && !callbacks.contains(c)) callbacks.add(c);
    }

    public void removeCallback(Callback c) {
        callbacks.remove(c);
    }

    private void fireUpdated() {
        for (Callback c : callbacks) c.onHealthUpdated();
    }

    // ---- pedidos ----

    /** Pede todo o historico que o relogio guarda (resumo, passos, sono, BPM, SpO2, temp). */
    public void syncAll() {
        if (!W26.get().isConnected()) {
            W26.get().note("saude: relogio desconectado");
            return;
        }
        W26.get().note("saude: pedindo dados (GET 1/2/3/4/8/9)");
        for (int n : new int[]{1, 2, 3, 4, 8, 9}) W26.get().sendTjd("X0,GET," + n);
        W26.get().sendTjd("X0,PS,GET");
    }

    /** Atualizacao periodica: so o resumo do dia + BPM atual. */
    public void periodic() {
        if (!W26.get().isConnected()) return;
        W26.get().sendTjd("X0,GET,1");
        W26.get().sendTjd("X0,GET,4");
    }

    /** Medicao ao vivo. type: hr | bp | spo2 | temp. O relogio mede e empurra o resultado. */
    public void measure(String type) {
        if (!W26.get().isConnected()) {
            W26.get().note("medir " + type + ": relogio desconectado");
            return;
        }
        String cmd;
        switch (type) {
            case "hr": cmd = "X1,SET,HrvOpen,1"; break;
            case "bp": cmd = "X1,SET,BldPrOpen,1"; break;
            case "spo2": cmd = "X1,SET,BOxyOpen,1"; break;
            case "temp": cmd = "X1,SET,BTmpOpen,1"; break;
            default: return;
        }
        measuring = type;
        W26.get().sendTjd(cmd);
        W26.get().note("medindo " + type + " no relogio… (fique parado)");
    }

    private void stopMeasure(String type) {
        String cmd;
        switch (type) {
            case "hr": cmd = "X1,SET,HrvOpen,0"; break;
            case "bp": cmd = "X1,SET,BldPrOpen,0"; break;
            case "spo2": cmd = "X1,SET,BOxyOpen,0"; break;
            case "temp": cmd = "X1,SET,BTmpOpen,0"; break;
            default: return;
        }
        W26.get().sendTjd(cmd);
        measuring = null;
    }

    // ---- recepcao (W26.Listener) ----

    @Override
    public void onLog(String line) {
    }

    @Override
    public void onState(String state) {
    }

    @Override
    public void onTjd(String frame) {
        String s = frame.trim();
        if (s.endsWith("#")) s = s.substring(0, s.length() - 1);
        try {
            if (s.startsWith("X0,GET,")) parseGet(s);
            else if (s.startsWith("X0,PS,GET,")) parseProfile(s);
            else if (s.contains("HrvDat,")) parseLiveHr(s);
            else if (s.contains("BldPrDat,")) parseLiveBp(s);
            else if (s.contains("BOxyPrDat,") || s.contains("BOxyDat,")) parseLivePipe(s, "spo2");
            else if (s.contains("BTmpPrDat,") || s.contains("BTmpDat,")) parseLivePipe(s, "temp");
        } catch (Exception e) {
            W26.get().note("saude parse: " + e.getMessage() + " em " + s);
        }
    }

    @Override
    public void onMtk(String text) {
    }

    private void parseGet(String s) {
        int n;
        try {
            n = Integer.parseInt(s.substring(7, s.indexOf(',', 7) < 0 ? s.length() : s.indexOf(',', 7)));
        } catch (Exception e) {
            return;
        }
        if (n != 1 && n != 2 && n != 3 && n != 4 && n != 8 && n != 9) return;
        String pfx = "X0,GET," + n + ",";
        if (!s.startsWith(pfx)) {
            if (n >= 1 && n <= 9) W26.get().sendTjd("X0,RET," + n);
            return;
        }
        String body = s.substring(pfx.length());
        if (body.isEmpty()) {
            W26.get().sendTjd("X0,RET," + n);
            return;
        }
        String[] recs = body.split(",");
        int saved = 0;
        for (String rec : recs) {
            String[] f = rec.split("\\|");
            if (saveRecord(n, f)) saved++;
        }
        W26.get().sendTjd("X0,RET," + n);
        if (saved > 0) {
            W26.get().note("saude GET," + n + ": " + saved + " registros");
            fireUpdated();
        }
    }

    /** Campos por indice (RE do BtPP_CH.java do app oficial). */
    private boolean saveRecord(int n, String[] f) {
        long ts;
        String day;
        ContentValues v = new ContentValues();
        switch (n) {
            case 1: // resumo: tipo|...
                if (f.length < 2) return false;
                if (f[0].equals("0") && f.length >= 5) { // passos do dia
                    ts = parseDate(f[1]);
                    v.put("type", "step");
                    v.put("v1", num(f[2]));
                    v.put("v2", num(f[3]));
                    v.put("v3", num(f[4]));
                } else if (f[0].equals("1") && f.length >= 5) { // sono do dia (duracao em segundos)
                    ts = parseDate(f[1]);
                    v.put("type", "sleep");
                    v.put("v1", num(f[2]));
                    v.put("v2", num(f[3]));
                    v.put("v3", num(f[4]));
                } else if (f[0].equals("3") && f.length >= 3) { // BPM
                    ts = parseDate(f[1]);
                    v.put("type", "hr");
                    v.put("v1", num(f[2]));
                } else return false;
                break;
            case 2: // datetime|step|distance|calorie
                if (f.length < 4) return false;
                ts = parseDate(f[0]);
                v.put("type", "step");
                v.put("v1", num(f[1]));
                v.put("v2", num(f[2]));
                v.put("v3", num(f[3]));
                break;
            case 3: // datetime|turnNum|slpMode
                if (f.length < 3) return false;
                ts = parseDate(f[0]);
                v.put("type", "sleepseg");
                v.put("v1", num(f[1]));
                v.put("v2", num(f[2]));
                v.put("v3", 0);
                break;
            case 4: // 0|bpm / 1|bpm / 3|datetime|bpm
                if (f.length >= 3 && f[0].equals("3")) {
                    ts = parseDate(f[1]);
                    v.put("v1", num(f[2]));
                } else if (f.length >= 2) {
                    ts = System.currentTimeMillis();
                    v.put("v1", num(f[1]));
                } else return false;
                v.put("type", "hr");
                break;
            case 8: // datetime|spo2
                if (f.length < 2) return false;
                ts = parseDate(f[0]);
                v.put("type", "spo2");
                v.put("v1", num(f[1]));
                break;
            case 9: // datetime|temp
                if (f.length < 2) return false;
                ts = parseDate(f[0]);
                v.put("type", "temp");
                v.put("v1", num(f[1]));
                break;
            default:
                return false;
        }
        day = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(ts));
        v.put("ts", ts);
        v.put("day", day);
        v.put("raw", String.join("|", f));
        db.getWritableDatabase().insert("samples", null, v);
        return true;
    }

    private void parseProfile(String s) {
        String body = s.substring("X0,PS,GET,".length());
        String[] f = body.split("\\|");
        if (f.length >= 3) {
            W26.get().info.put("goalStep", f[0]);
            W26.get().info.put("height", f[1]);
            W26.get().info.put("weight", f[2]);
        }
    }

    private void parseLiveHr(String s) {
        String body = s.substring(s.indexOf("HrvDat,") + 7);
        double bpm = num(body.split("[|,]")[0]);
        if (bpm <= 0) return; // 0 = ainda medindo
        storeLive("hr", bpm, 0);
        stopMeasure("hr");
        W26.get().note("BPM medido: " + (int) bpm);
        for (Callback c : callbacks) c.onMeasure("hr", bpm);
        fireUpdated();
    }

    private void parseLiveBp(String s) {
        String body = s.substring(s.indexOf("BldPrDat,") + 9);
        String[] f = body.split("[|,]");
        if (f.length < 2) return;
        double sys = num(f[0]), dia = num(f[1]);
        if (sys <= 0) return;
        storeLive("bp", sys, dia);
        stopMeasure("bp");
        W26.get().note("pressao medida: " + (int) sys + "/" + (int) dia);
        for (Callback c : callbacks) c.onMeasure("bp", sys);
        fireUpdated();
    }

    private void parseLivePipe(String s, String type) {
        int idx = s.indexOf(type.equals("spo2") ? "BOxy" : "BTmp");
        int comma = s.indexOf(',', idx);
        String body = s.substring(comma + 1);
        String[] f = body.split("\\|");
        double val = num(f[f.length - 1]);
        if (val <= 0) return;
        long ts = f.length >= 2 ? parseDate(f[0]) : System.currentTimeMillis();
        storeLive(type, val, 0, ts);
        stopMeasure(type);
        W26.get().note((type.equals("spo2") ? "SpO2" : "temperatura") + " medido: " + val);
        for (Callback c : callbacks) c.onMeasure(type, val);
        fireUpdated();
    }

    private void storeLive(String type, double v1, double v2) {
        storeLive(type, v1, v2, System.currentTimeMillis());
    }

    private void storeLive(String type, double v1, double v2, long ts) {
        ContentValues v = new ContentValues();
        v.put("ts", ts);
        v.put("day", new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(ts)));
        v.put("type", type);
        v.put("v1", v1);
        v.put("v2", v2);
        v.put("v3", 0);
        v.put("raw", "live");
        db.getWritableDatabase().insert("samples", null, v);
    }

    // ---- consultas ----

    public static final class DayStat {
        public String day;
        public int steps;
        public double distanceKm, calorie, sleepHours, hrAvg, hrMin, hrMax, spo2, temp, bpSys, bpDia;
    }

    public DayStat today() {
        return dayStat(new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()));
    }

    public DayStat dayStat(String day) {
        DayStat d = new DayStat();
        d.day = day;
        SQLiteDatabase r = db.getReadableDatabase();
        Cursor c = r.rawQuery("SELECT type,v1,v2,v3 FROM samples WHERE day=?", new String[]{day});
        double hrSum = 0, hrN = 0, spo2Sum = 0, spo2N = 0, tempSum = 0, tempN = 0;
        d.hrMin = 999;
        while (c.moveToNext()) {
            String t = c.getString(0);
            double v1 = c.getDouble(1), v2 = c.getDouble(2), v3 = c.getDouble(3);
            switch (t) {
                case "step":
                    if (v1 > d.steps) {
                        d.steps = (int) v1;
                        d.distanceKm = v2 / 10000.0; // raw/10 = m, /1000 = km
                        d.calorie = v3 / 10.0;       // raw/10 = kcal
                    }
                    break;
                case "sleep":
                    if (v1 / 3600.0 > d.sleepHours) d.sleepHours = v1 / 3600.0; // mAllTimLen em segundos
                    break;
                case "hr":
                    if (v1 > 0) {
                        hrSum += v1;
                        hrN++;
                        d.hrMin = Math.min(d.hrMin, v1);
                        d.hrMax = Math.max(d.hrMax, v1);
                    }
                    break;
                case "spo2":
                    if (v1 > 0) { spo2Sum += v1; spo2N++; }
                    break;
                case "temp":
                    if (v1 > 0) { tempSum += v1; tempN++; }
                    break;
                case "bp":
                    d.bpSys = v1;
                    d.bpDia = v2;
                    break;
            }
        }
        c.close();
        d.hrAvg = hrN > 0 ? hrSum / hrN : 0;
        if (d.hrMin == 999) d.hrMin = 0;
        d.spo2 = spo2N > 0 ? spo2Sum / spo2N : 0;
        d.temp = tempN > 0 ? (tempSum / tempN) / 10.0 : 0; // wire em decimos de °C
        return d;
    }

    public List<DayStat> lastDays(int n) {
        List<DayStat> out = new ArrayList<>();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        long now = System.currentTimeMillis();
        for (int i = n - 1; i >= 0; i--) {
            out.add(dayStat(fmt.format(new Date(now - i * 24L * 3600_000L))));
        }
        return out;
    }

    public JSONObject summaryJson() {
        JSONObject j = new JSONObject();
        try {
            DayStat d = today();
            j.put("day", d.day).put("steps", d.steps)
                    .put("distanceKm", round(d.distanceKm)).put("calorie", round(d.calorie))
                    .put("sleepHours", round(d.sleepHours)).put("hrAvg", round(d.hrAvg))
                    .put("hrMin", d.hrMin).put("hrMax", d.hrMax)
                    .put("spo2", round(d.spo2)).put("temp", round(d.temp))
                    .put("bpSys", d.bpSys).put("bpDia", d.bpDia);
        } catch (Exception ignored) {
        }
        return j;
    }

    public JSONObject historyJson(int days) {
        JSONObject j = new JSONObject();
        try {
            JSONArray arr = new JSONArray();
            for (DayStat d : lastDays(days)) {
                JSONObject o = new JSONObject();
                o.put("day", d.day).put("steps", d.steps).put("distanceKm", round(d.distanceKm))
                        .put("calorie", round(d.calorie)).put("sleepHours", round(d.sleepHours))
                        .put("hrAvg", round(d.hrAvg)).put("hrMin", d.hrMin).put("hrMax", d.hrMax)
                        .put("spo2", round(d.spo2)).put("temp", round(d.temp));
                arr.put(o);
            }
            j.put("days", arr);
        } catch (Exception ignored) {
        }
        return j;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double num(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /** Aceita epoch (s/ms), "yyyy-MM-dd HH:mm:ss", "yyyyMMddHHmmss", "yyyy/MM/dd HH:mm". */
    static long parseDate(String s) {
        s = s.trim();
        long now = System.currentTimeMillis();
        if (s.isEmpty()) return now;
        if (s.matches("\\d{10}")) return Long.parseLong(s) * 1000L;
        if (s.matches("\\d{13}")) return Long.parseLong(s);
        String[] fmts = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm:ss",
                "yyyy/MM/dd HH:mm", "yyyyMMddHHmmss", "yyyy-MM-dd", "MM-dd HH:mm", "HH:mm:ss", "HH:mm"};
        for (String f : fmts) {
            try {
                Date d = new SimpleDateFormat(f, Locale.ROOT).parse(s);
                if (d != null) {
                    if (f.equals("HH:mm:ss") || f.equals("HH:mm") || f.equals("MM-dd HH:mm")) {
                        java.util.Calendar c = java.util.Calendar.getInstance();
                        c.setTime(d);
                        java.util.Calendar t = java.util.Calendar.getInstance();
                        t.setTime(new Date(now));
                        c.set(java.util.Calendar.YEAR, t.get(java.util.Calendar.YEAR));
                        if (f.startsWith("HH")) {
                            c.set(java.util.Calendar.MONTH, t.get(java.util.Calendar.MONTH));
                            c.set(java.util.Calendar.DAY_OF_MONTH, t.get(java.util.Calendar.DAY_OF_MONTH));
                        }
                        return c.getTimeInMillis();
                    }
                    return d.getTime();
                }
            } catch (Exception ignored) {
            }
        }
        return now;
    }

    private static final class Db extends SQLiteOpenHelper {
        Db(Context c) {
            super(c, "health.db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE samples(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, day TEXT, type TEXT, v1 REAL, v2 REAL, v3 REAL, raw TEXT)");
            d.execSQL("CREATE INDEX idx_day ON samples(day)");
            d.execSQL("CREATE INDEX idx_type ON samples(type, ts)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase d, int a, int b) {
        }
    }
}