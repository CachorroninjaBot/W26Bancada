package com.luiz.w26;

import android.content.Context;
import android.content.Intent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Log persistente: um arquivo por dia em filesDir/logs, retencao de 14 dias,
 *  ring buffer em memoria (com seq) para a UI e para a API de diagnostico. */
public final class LogStore {
    public static final class Line {
        public final long seq;
        public final long ts;
        public final String text;

        Line(long seq, long ts, String text) {
            this.seq = seq;
            this.ts = ts;
            this.text = text;
        }
    }

    private static final int KEEP_DAYS = 14;
    private static final long MAX_FILE = 1024 * 1024;
    private static final int RING = 600;

    private static File dir;
    private static String today = "";
    private static FileWriter writer;
    private static long seq;
    private static final ArrayDeque<Line> ring = new ArrayDeque<>();
    private static final Object LOCK = new Object();

    private LogStore() {
    }

    public static void init(Context c) {
        synchronized (LOCK) {
            if (dir != null) return;
            dir = new File(c.getApplicationContext().getFilesDir(), "logs");
            if (!dir.exists()) dir.mkdirs();
            purgeOld();
        }
    }

    public static void append(String text) {
        long now = System.currentTimeMillis();
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date(now));
        String line = stamp + "  " + text;
        synchronized (LOCK) {
            seq++;
            ring.addLast(new Line(seq, now, line));
            while (ring.size() > RING) ring.removeFirst();
            if (dir == null) return;
            try {
                String day = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(now));
                if (!day.equals(today) || writer == null) {
                    closeWriter();
                    today = day;
                    File f = new File(dir, day + ".log");
                    if (f.length() > MAX_FILE) {
                        File bak = new File(dir, day + ".1.log");
                        if (bak.exists()) bak.delete();
                        f.renameTo(bak);
                    }
                    writer = new FileWriter(f, true);
                }
                writer.write(line);
                writer.write('\n');
                writer.flush();
            } catch (IOException ignored) {
            }
        }
    }

    /** Linhas do ring buffer com seq maior que since (para tail em tempo real). */
    public static List<Line> since(long since) {
        synchronized (LOCK) {
            List<Line> out = new ArrayList<>();
            for (Line l : ring) if (l.seq > since) out.add(l);
            return out;
        }
    }

    public static long lastSeq() {
        synchronized (LOCK) {
            return seq;
        }
    }

    /** Dias disponiveis, mais recente primeiro ("yyyy-MM-dd"). */
    public static List<String> dates() {
        synchronized (LOCK) {
            List<String> out = new ArrayList<>();
            if (dir == null) return out;
            File[] fs = dir.listFiles();
            if (fs == null) return out;
            for (File f : fs) {
                String n = f.getName();
                if (n.endsWith(".log") && !n.endsWith(".1.log")) out.add(n.substring(0, n.length() - 4));
            }
            java.util.Collections.sort(out, java.util.Collections.reverseOrder());
            return out;
        }
    }

    /** Conteudo do dia (cauda de ate 256 KB para nao estourar a UI). */
    public static String read(String day) {
        synchronized (LOCK) {
            if (dir == null) return "";
            File f = new File(dir, day + ".log");
            if (!f.exists()) return "";
            try {
                long len = f.length();
                long skip = Math.max(0, len - 256 * 1024);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                    if (skip > 0) {
                        long s = 0;
                        while (s < skip) {
                            long n = r.skip(skip - s);
                            if (n <= 0) break;
                            s += n;
                        }
                        r.readLine();
                    }
                    String l;
                    while ((l = r.readLine()) != null) sb.append(l).append('\n');
                }
                return sb.toString();
            } catch (IOException e) {
                return "erro lendo log: " + e.getMessage();
            }
        }
    }

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
    }

    public static void clear(String day) {
        synchronized (LOCK) {
            if (dir == null) return;
            if (day.equals(today)) closeWriter();
            File f = new File(dir, day + ".log");
            if (f.exists()) f.delete();
            File bak = new File(dir, day + ".1.log");
            if (bak.exists()) bak.delete();
            if (day.equals(today)) {
                ring.clear();
                seq = 0;
            }
        }
    }

    /** Compartilhar o dia como texto (sem FileProvider: EXTRA_TEXT direto). */
    public static Intent shareIntent(String day) {
        String body = read(day);
        if (body.length() > 100_000) body = body.substring(body.length() - 100_000);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "W26 log " + day);
        i.putExtra(Intent.EXTRA_TEXT, body.isEmpty() ? "(log vazio)" : body);
        return Intent.createChooser(i, "Exportar log " + day);
    }

    private static void closeWriter() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
            writer = null;
        }
    }

    private static void purgeOld() {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        long cutoff = System.currentTimeMillis() - KEEP_DAYS * 24L * 3600_000L;
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        for (File f : fs) {
            String n = f.getName();
            if (!n.endsWith(".log")) continue;
            String day = n.substring(0, Math.min(10, n.length()));
            try {
                Date d = fmt.parse(day);
                if (d != null && d.getTime() < cutoff) f.delete();
            } catch (Exception ignored) {
            }
        }
    }
}