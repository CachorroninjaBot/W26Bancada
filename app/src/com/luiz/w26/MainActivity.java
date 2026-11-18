package com.luiz.w26;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** W26: bancada bonita + sincronizacao celular<->relogio (hora, notificacoes, bateria, finder). */
public class MainActivity extends Activity implements W26.Listener {
    private static final String DEFAULT_MAC = "C2:BA:45:15:C8:F9";
    private static final String DEFAULT_CLASSIC = "06:87:E6:0C:62:61";
    private static final int REQ_VXP = 7;

    private static final int BG = 0xFF0B0F14;
    private static final int CARD = 0xFF131A23;
    private static final int STROKE = 0xFF223041;
    private static final int FIELD = 0xFF0E141B;
    private static final int GHOST = 0xFF1C2733;
    private static final int ACCENT = 0xFF38BDF8;
    private static final int GREEN = 0xFF34D399;
    private static final int WARN = 0xFFF87171;
    private static final int AMBER = 0xFFFBBF24;
    private static final int TEXT = 0xFFE6EDF3;
    private static final int MUTED = 0xFF8B98A9;
    private static final int DARK = 0xFF0B0F14;

    private TextView stateView;
    private TextView watchStateView;
    private TextView watchBattView;
    private TextView phoneBattView;
    private TextView gpsView;
    private TextView lastSyncView;
    private TextView notifView;
    private TextView notifAccessView;
    private TextView finderView;
    private TextView logView;
    private ScrollView logScroll;
    private EditText macView;
    private EditText classicView;
    private EditText tjdCmd;
    private EditText mtkHex;
    private EditText vxpName;
    private Switch autoSyncSwitch;
    private Switch notifSwitch;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Ringtone alertRing;
    private Vibrator alertVib;
    private final Runnable alertStop = this::stopAlert;
    private final Runnable periodic = new Runnable() {
        @Override
        public void run() {
            if (W26.get().isConnected()) W26.get().battery();
            refreshStatus();
            ui.postDelayed(this, 30000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        W26.get().setListener(this);
        createChannel();
        setContentView(buildUi());
        requestPermissions();
        refreshFinder();
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFinder();
        refreshStatus();
        ui.removeCallbacks(periodic);
        ui.postDelayed(periodic, 30000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(periodic);
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(14), dp(16), dp(16));

        // ---- Header ----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("W26");
        title.setTextSize(30);
        title.setTextColor(TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titles.addView(title);
        TextView sub = new TextView(this);
        sub.setText("bancada · sync");
        sub.setTextSize(12);
        sub.setTextColor(MUTED);
        titles.addView(sub);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stateView = chip("desconectado", GHOST, TEXT);
        header.addView(stateView);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = dp(14);
        root.addView(header, hlp);

        LinearLayout ctrl = new LinearLayout(this);
        ctrl.setOrientation(LinearLayout.VERTICAL);

        // ---- Status ----
        LinearLayout status = card();
        status.addView(sectionTitle("Status"));
        watchStateView = statValue("—");
        watchBattView = statValue("—");
        phoneBattView = statValue("—");
        gpsView = statValue("—");
        lastSyncView = statValue("—");
        notifView = statValue("—");
        status.addView(statRow(stat("Relógio", watchStateView), stat("Bateria relógio", watchBattView)));
        status.addView(statRow(stat("Bateria celular", phoneBattView), stat("GPS", gpsView)));
        status.addView(statRow(stat("Última sync", lastSyncView), stat("Notificações", notifView)));
        ctrl.addView(status);

        // ---- Conexao ----
        LinearLayout conn = card();
        conn.addView(sectionTitle("Conexão"));
        macView = field(DEFAULT_MAC);
        classicView = field(DEFAULT_CLASSIC);
        conn.addView(labeled("BLE (GATT)", macView));
        conn.addView(labeled("Clássico (SPP)", classicView));
        conn.addView(row(
                primaryBtn("Conectar BLE", v -> W26.get().connect(this, macView.getText().toString().trim())),
                primaryBtn("Conectar SPP", v -> W26.get().connectSpp(this, classicView.getText().toString().trim()))));
        conn.addView(row(
                ghostBtn("Desconectar", v -> W26.get().disconnect()),
                ghostBtn("SPP off", v -> W26.get().disconnectSpp())));
        ctrl.addView(conn);

        // ---- Sync ----
        LinearLayout sync = card();
        sync.addView(sectionTitle("Sincronização"));
        autoSyncSwitch = makeSwitch(Prefs.autoSync(this));
        autoSyncSwitch.setOnCheckedChangeListener((b, c) -> Prefs.setAutoSync(this, c));
        sync.addView(toggleRow("Hora automática ao conectar", autoSyncSwitch));
        notifSwitch = makeSwitch(Prefs.notifEnabled(this));
        notifSwitch.setOnCheckedChangeListener((b, c) -> {
            Prefs.setNotifEnabled(this, c);
            refreshStatus();
        });
        sync.addView(toggleRow("Encaminhar notificações", notifSwitch));
        notifAccessView = new TextView(this);
        notifAccessView.setTextSize(11);
        notifAccessView.setTextColor(MUTED);
        notifAccessView.setPadding(0, dp(2), 0, dp(6));
        sync.addView(notifAccessView);
        sync.addView(row(
                primaryBtn("Sincronizar agora", v -> {
                    W26.get().syncTime();
                    W26.get().battery();
                    Prefs.markSync(this);
                    refreshStatus();
                    log("sync: hora + bateria pedidos");
                }),
                ghostBtn("Acesso notificações", v -> startActivity(
                        new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))));
        sync.addView(row(
                ghostBtn("Bateria do relógio", v -> W26.get().battery()),
                ghostBtn("Notificação teste", v -> W26.get().notifyWatch(
                        "W26", "Teste do celular", "Se você está lendo isso, funcionou!"))));
        sync.addView(row(
                ghostBtn("Testar alerta do celular", v -> alertPhone()),
                ghostBtn("Enviar clima", v -> Weather.fetchAndPush(this))));
        ctrl.addView(sync);

        // ---- Finder ----
        LinearLayout find = card();
        find.addView(sectionTitle("Finder / localizador"));
        TextView note = new TextView(this);
        note.setText("O relógio não tem GPS: guardamos a última posição do celular e fazemos o relógio vibrar.");
        note.setTextSize(12);
        note.setTextColor(MUTED);
        note.setPadding(0, 0, 0, dp(8));
        find.addView(note);
        finderView = new TextView(this);
        finderView.setTextSize(13);
        finderView.setTextColor(TEXT);
        finderView.setPadding(0, 0, 0, dp(8));
        find.addView(finderView);
        find.addView(row(
                primaryBtn("Fazer vibrar", v -> W26.get().alertWatch(2)),
                ghostBtn("Atualizar GPS", v -> {
                    Finder.capture(this);
                    refreshFinder();
                    refreshStatus();
                    log("finder: pedido fix de GPS");
                })));
        find.addView(ghostBtn("Abrir no mapa", v -> {
            if (!Finder.hasFix(this)) {
                log("finder: sem posição guardada");
                return;
            }
            try {
                startActivity(Finder.mapsIntent(this));
            } catch (Exception e) {
                log("finder: sem app de mapa (" + e.getMessage() + ")");
            }
        }));
        ctrl.addView(find);

        // ---- Mostradores & apps ----
        LinearLayout mre = card();
        mre.addView(sectionTitle("Mostradores & apps (MRE / SPP)"));
        mre.addView(row(
                primaryBtn("LuizFace RES", v -> installAsset("luizface3.vxp", "luizface3.vxp")),
                primaryBtn("Face REF", v -> installAsset("ref.vxp", "refface.vxp"))));
        mre.addView(row(
                ghostBtn("LuizFace", v -> installAsset("luizface.vxp", "luizface.vxp")),
                ghostBtn("LuizApp", v -> installAsset("luizapp.vxp", "luizapp.vxp"))));
        mre.addView(ghostBtn("LuizFace 240 (teste ponteiros)", v -> installAsset("luizface4.vxp", "luizface240.vxp")));
        mre.addView(row(
                ghostBtn("Instalar VXP…", v -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, REQ_VXP);
                }),
                ghostBtn("MREE listar", v -> W26.get().mreeList())));
        vxpName = field("luizface3.vxp");
        mre.addView(labeled("Nome do VXP", vxpName));
        mre.addView(row(
                ghostBtn("Status", v -> W26.get().mreeStatus(vxpName())),
                ghostBtn("Permissões", v -> W26.get().mreeSetPermission(vxpName(), 0xFFFFFFFF))));
        mre.addView(row(
                ghostBtn("Apagar", v -> W26.get().mreeUninstall(vxpName())),
                dangerBtn("Excluir", v -> W26.get().mreeDelete(vxpName()))));
        mre.addView(row(
                dangerBtn("Desinstalar TODOS", v -> W26.get().mreeUninstallAll()),
                ghostBtn("Limpar testes", v -> cleanTests())));
        ctrl.addView(mre);

        // ---- Console (colapsavel) ----
        LinearLayout advBody = new LinearLayout(this);
        advBody.setOrientation(LinearLayout.VERTICAL);
        tjdCmd = field("X1,LINK");
        advBody.addView(labeled("TJD (sem $LHBT)", tjdCmd));
        advBody.addView(ghostBtn("Enviar TJD", v -> {
            String c = tjdCmd.getText().toString().trim();
            if (!c.isEmpty()) W26.get().sendTjd(c);
        }));
        mtkHex = field("SYNC REQV");
        advBody.addView(labeled("MTK cru (hex ou texto)", mtkHex));
        advBody.addView(row(
                ghostBtn("Enviar texto", v -> {
                    String s = mtkHex.getText().toString().trim();
                    if (!s.isEmpty()) W26.get().sendMtkAuto(Mtk.cmd(s));
                }),
                ghostBtn("Enviar hex", v -> sendHex())));
        ctrl.addView(collapsible("Console de protocolo", advBody));

        // ---- Log (colapsavel) ----
        LinearLayout logBody = new LinearLayout(this);
        logBody.setOrientation(LinearLayout.VERTICAL);
        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTextColor(MUTED);
        logView.setTypeface(Typeface.MONOSPACE);
        logScroll = new ScrollView(this);
        logScroll.addView(logView);
        logBody.addView(logScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
        logBody.addView(ghostBtn("Limpar log", v -> logView.setText("")));
        ctrl.addView(collapsible("Log", logBody));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(ctrl);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    // ---- acoes ----

    private String vxpName() {
        return vxpName.getText().toString().trim();
    }

    private void installAsset(String asset, String name) {
        try {
            W26.get().mreeInstall(name, asset(asset));
        } catch (Exception e) {
            log("asset " + asset + ": " + e.getMessage());
        }
    }

    private void cleanTests() {
        for (String n : new String[]{"qoder.vxp", "qoder2.vxp", "qodertest.vxp", "face2.vxp",
                "luizface.vxp", "luizface2.vxp", "luizface3.vxp", "luizapp.vxp", "refface.vxp"})
            W26.get().mreeUninstall(n);
        log("limpeza: uninstall pedido para os vxp de teste");
    }

    private void sendHex() {
        String s = mtkHex.getText().toString().replace(" ", "").trim();
        try {
            byte[] b = new byte[s.length() / 2];
            for (int i = 0; i < b.length; i++)
                b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
            W26.get().sendMtkAuto(b);
        } catch (Exception e) {
            log("hex inválido: " + e.getMessage());
        }
    }

    private void alertPhone() {
        stopAlert();
        try {
            android.net.Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            alertRing = RingtoneManager.getRingtone(this, uri);
            if (alertRing != null) {
                if (Build.VERSION.SDK_INT >= 28) alertRing.setLooping(true);
                alertRing.play();
            }
        } catch (Exception e) {
            log("alerta celular: " + e.getMessage());
        }
        try {
            alertVib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (alertVib != null)
                alertVib.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 400, 500, 400, 500}, -1));
        } catch (Exception ignored) {
        }
        ui.postDelayed(alertStop, 11000);
        log("Encontrar celular: tocando alerta (para em 11s)");
    }

    private void stopAlert() {
        ui.removeCallbacks(alertStop);
        try {
            if (alertRing != null) alertRing.stop();
        } catch (Exception ignored) {
        }
        alertRing = null;
        try {
            if (alertVib != null) alertVib.cancel();
        } catch (Exception ignored) {
        }
        alertVib = null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel("w26", "W26", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }
    }

    private void notifyPhone(String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification n = new Notification.Builder(this, "w26")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build();
        nm.notify(1, n);
    }

    // ---- estado / sync ----

    private void refreshFinder() {
        if (finderView != null) finderView.setText(Finder.describe(this));
    }

    private void refreshStatus() {
        int wb = W26.get().getWatchBattery();
        watchBattView.setText(wb < 0 ? "—" : wb + "%");
        phoneBattView.setText(phoneBattery() + "%");
        gpsView.setText(Finder.hasFix(this) ? "ok" : "—");
        long ls = Prefs.lastSync(this);
        lastSyncView.setText(ls == 0 ? "nunca"
                : new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date(ls)));
        boolean access = notifAccess();
        boolean on = Prefs.notifEnabled(this) && access;
        notifView.setText(on ? "ativo" : "inativo");
        notifView.setTextColor(on ? GREEN : MUTED);
        notifAccessView.setText(access ? "Acesso a notificações concedido."
                : "Sem acesso: toque em “Acesso notificações” e ative o W26.");
    }

    private int phoneBattery() {
        try {
            Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b == null) return -1;
            int lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            return scale > 0 ? lvl * 100 / scale : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean notifAccess() {
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    @Override
    public void onBattery(int percent) {
        watchBattView.setText(percent + "%");
        log("bateria do relógio: " + percent + "%");
    }

    @Override
    public void onFindPhone(boolean start) {
        if (start) {
            log("relógio pediu: encontrar celular");
            alertPhone();
            notifyPhone("Encontrar celular", "O relógio pediu para localizar este celular.");
        } else {
            log("relógio parou o alerta");
            stopAlert();
        }
    }

    @Override
    public void onMusic(String action) {
        String a = action.toLowerCase(java.util.Locale.ROOT);
        int key;
        if (a.equals("play")) key = KeyEvent.KEYCODE_MEDIA_PLAY;
        else if (a.equals("pause")) key = KeyEvent.KEYCODE_MEDIA_PAUSE;
        else if (a.contains("next")) key = KeyEvent.KEYCODE_MEDIA_NEXT;
        else if (a.contains("prev")) key = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
        else if (a.contains("vol+") || a.contains("up")) key = KeyEvent.KEYCODE_VOLUME_UP;
        else if (a.contains("vol-") || a.contains("down")) key = KeyEvent.KEYCODE_VOLUME_DOWN;
        else key = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
        log("música: " + action);
    }

    // ---- helpers de UI ----

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), STROKE);
        c.setBackground(bg);
        c.setPadding(dp(16), dp(14), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView sectionTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text.toUpperCase(java.util.Locale.ROOT));
        t.setTextSize(11);
        t.setTextColor(MUTED);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, 0, 0, dp(10));
        return t;
    }

    private TextView statValue(String v) {
        TextView t = new TextView(this);
        t.setText(v);
        t.setTextSize(16);
        t.setTextColor(TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private View stat(String label, TextView value) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextSize(11);
        lab.setTextColor(MUTED);
        l.addView(lab);
        l.addView(value);
        l.setPadding(0, dp(4), dp(8), dp(6));
        return l;
    }

    private View statRow(View... tiles) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (View v : tiles)
            r.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return r;
    }

    private TextView chip(String text, int bg, int fg) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(fg);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(20));
        t.setBackground(g);
        t.setPadding(dp(12), dp(5), dp(12), dp(5));
        return t;
    }

    private Button btnBase(String text, int bg, int fg, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(fg);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(12));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), g, null));
        b.setPadding(dp(12), dp(11), dp(12), dp(11));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private Button primaryBtn(String text, View.OnClickListener l) {
        return btnBase(text, ACCENT, DARK, l);
    }

    private Button ghostBtn(String text, View.OnClickListener l) {
        return btnBase(text, GHOST, TEXT, l);
    }

    private Button dangerBtn(String text, View.OnClickListener l) {
        return btnBase(text, WARN, DARK, l);
    }

    private View row(View... views) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < views.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.topMargin = dp(6);
            if (i < views.length - 1) lp.rightMargin = dp(6);
            r.addView(views[i], lp);
        }
        return r;
    }

    private Switch makeSwitch(boolean checked) {
        Switch s = new Switch(this);
        s.setChecked(checked);
        s.setThumbTintList(ColorStateList.valueOf(ACCENT));
        s.setTrackTintList(ColorStateList.valueOf(0xFF2A3A4D));
        return s;
    }

    private View toggleRow(String label, Switch sw) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14);
        t.setTextColor(TEXT);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(sw);
        r.setPadding(0, dp(4), 0, dp(4));
        return r;
    }

    private View collapsible(String title, View content) {
        LinearLayout c = card();
        content.setVisibility(View.GONE);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView t = sectionTitle(title);
        t.setPadding(0, 0, 0, 0);
        head.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = new TextView(this);
        arrow.setText("▸");
        arrow.setTextColor(MUTED);
        arrow.setTextSize(14);
        head.addView(arrow);
        head.setOnClickListener(v -> {
            boolean show = content.getVisibility() != View.VISIBLE;
            content.setVisibility(show ? View.VISIBLE : View.GONE);
            arrow.setText(show ? "▾" : "▸");
        });
        c.addView(head);
        c.addView(content);
        return c;
    }

    private EditText field(String text) {
        EditText e = new EditText(this);
        e.setText(text);
        e.setTextSize(13);
        e.setSingleLine(true);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(FIELD);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), STROKE);
        e.setBackground(bg);
        e.setPadding(dp(12), dp(9), dp(12), dp(9));
        return e;
    }

    private View labeled(String label, View field) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(11);
        t.setTextColor(MUTED);
        t.setPadding(0, dp(6), 0, dp(3));
        l.addView(t);
        l.addView(field);
        return l;
    }

    private byte[] asset(String name) throws Exception {
        try (java.io.InputStream in = getAssets().open(name)) {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int r;
            while ((r = in.read(b)) > 0) o.write(b, 0, r);
            return o.toByteArray();
        }
    }

    private void requestPermissions() {
        java.util.List<String> want = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                want.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                want.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            want.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!Finder.hasPermission(this)) {
            want.add(Manifest.permission.ACCESS_FINE_LOCATION);
            want.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (!want.isEmpty()) requestPermissions(want.toArray(new String[0]), 1);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_VXP || res != RESULT_OK || data == null || data.getData() == null) return;
        android.net.Uri uri = data.getData();
        String name = "app.vxp";
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && c.getString(i) != null) name = c.getString(i);
            }
        } catch (Exception ignored) {
        }
        name = name.substring(name.lastIndexOf('/') + 1);
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int r;
            while ((r = in.read(b)) > 0) o.write(b, 0, r);
            byte[] vxp = o.toByteArray();
            log("VXP " + name + " (" + vxp.length + " bytes)");
            if (vxpName != null) vxpName.setText(name);
            W26.get().mreeInstall(name, vxp);
        } catch (Exception e) {
            log("VXP erro: " + e.getMessage());
        }
    }

    @Override
    public void onLog(String line) {
        log(line);
    }

    @Override
    public void onState(String s) {
        boolean up = "conectado".equals(s);
        stateView.setText(s);
        GradientDrawable g = new GradientDrawable();
        g.setColor(up ? GREEN : GHOST);
        g.setCornerRadius(dp(20));
        stateView.setBackground(g);
        stateView.setTextColor(up ? DARK : TEXT);
        watchStateView.setText(up ? "conectado" : s);
        watchStateView.setTextColor(up ? GREEN : MUTED);
        if (up) {
            Finder.capture(this);
            refreshFinder();
            W26.get().battery();
            if (Prefs.autoSync(this)) {
                W26.get().syncTime();
                Prefs.markSync(this);
                log("sync automático: hora enviada");
                Weather.fetchAndPush(this);
            }
        }
        refreshStatus();
    }

    private void log(String line) {
        CharSequence old = logView.getText();
        String all = old.toString() + java.time.LocalTime.now().withNano(0) + "  " + line + "\n";
        if (all.length() > 24000) all = all.substring(all.length() - 20000);
        logView.setText(all);
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }
}