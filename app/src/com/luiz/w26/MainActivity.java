package com.luiz.w26;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Bancada W26: conexao BLE/SPP, mostradores/apps MRE, finder e console de protocolo. */
public class MainActivity extends Activity implements W26.Listener {
    private static final String DEFAULT_MAC = "C2:BA:45:15:C8:F9";
    private static final String DEFAULT_CLASSIC = "06:87:E6:0C:62:61";
    private static final int REQ_VXP = 7;

    private static final int BG = 0xFF0F1419;
    private static final int CARD = 0xFF1A2129;
    private static final int ACCENT = 0xFF29B6F6;
    private static final int GREEN = 0xFF66BB6A;
    private static final int WARN = 0xFFEF5350;
    private static final int TEXT = 0xFFECEFF1;
    private static final int MUTED = 0xFF90A4AE;

    private TextView stateView;
    private TextView logView;
    private TextView finderView;
    private ScrollView logScroll;
    private EditText macView;
    private EditText classicView;
    private EditText tjdCmd;
    private EditText mtkHex;
    private EditText vxpName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        W26.get().setListener(this);
        setContentView(buildUi());
        requestPermissions();
        refreshFinder();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFinder();
    }

    private View buildUi() {
        int pad = dp(14);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout ctrl = new LinearLayout(this);
        ctrl.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("W26 Bancada");
        title.setTextSize(24);
        title.setTextColor(TEXT);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        ctrl.addView(title);

        stateView = new TextView(this);
        stateView.setText("desconectado");
        stateView.setTextSize(14);
        stateView.setTextColor(ACCENT);
        stateView.setPadding(0, dp(2), 0, dp(10));
        ctrl.addView(stateView);

        // ---- Conexao ----
        LinearLayout conn = card();
        conn.addView(header("Conexao"));
        macView = field(DEFAULT_MAC);
        classicView = field(DEFAULT_CLASSIC);
        conn.addView(labeled("BLE (GATT)", macView));
        conn.addView(labeled("Classico (SPP)", classicView));
        conn.addView(row(
                btn("Conectar BLE", ACCENT, v -> W26.get().connect(this, macView.getText().toString().trim())),
                btn("Conectar SPP", ACCENT, v -> W26.get().connectSpp(this, classicView.getText().toString().trim()))));
        conn.addView(row(
                btn("Desconectar", 0xFF37474F, v -> W26.get().disconnect()),
                btn("SPP off", 0xFF37474F, v -> W26.get().disconnectSpp())));
        ctrl.addView(conn);

        // ---- Finder ----
        LinearLayout find = card();
        find.addView(header("Finder / localizador"));
        TextView note = new TextView(this);
        note.setText("O relogio nao tem GPS: guarda a ultima posicao do celular e faz o relogio vibrar.");
        note.setTextSize(12);
        note.setTextColor(MUTED);
        note.setPadding(0, 0, 0, dp(6));
        find.addView(note);
        finderView = new TextView(this);
        finderView.setTextSize(13);
        finderView.setTextColor(TEXT);
        finderView.setPadding(0, 0, 0, dp(8));
        find.addView(finderView);
        find.addView(row(
                btn("Fazer vibrar", GREEN, v -> W26.get().alertWatch(2)),
                btn("Atualizar GPS", ACCENT, v -> {
                    Finder.capture(this);
                    refreshFinder();
                    log("finder: pedido fix de GPS");
                })));
        find.addView(btn("Abrir no mapa", ACCENT, v -> {
            if (!Finder.hasFix(this)) {
                log("finder: sem posicao guardada");
                return;
            }
            try {
                startActivity(Finder.mapsIntent(this));
            } catch (Exception e) {
                log("finder: sem app de mapa (" + e.getMessage() + ")");
            }
        }));
        ctrl.addView(find);

        // ---- Mostradores & apps MRE ----
        LinearLayout mre = card();
        mre.addView(header("Mostradores & apps (MRE / SPP)"));
        mre.addView(row(
                btn("Instalar LuizFace RES", ACCENT, v -> installAsset("luizface3.vxp", "luizface3.vxp")),
                btn("Instalar face REF", ACCENT, v -> installAsset("ref.vxp", "refface.vxp"))));
        mre.addView(row(
                btn("Instalar LuizFace", 0xFF37474F, v -> installAsset("luizface.vxp", "luizface.vxp")),
                btn("Instalar LuizApp", 0xFF37474F, v -> installAsset("luizapp.vxp", "luizapp.vxp"))));
        mre.addView(row(
                btn("Instalar VXP...", 0xFF37474F, v -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, REQ_VXP);
                }),
                btn("MREE listar", 0xFF37474F, v -> W26.get().mreeList())));
        vxpName = field("luizface3.vxp");
        mre.addView(labeled("Nome do VXP", vxpName));
        mre.addView(row(
                btn("Status", 0xFF37474F, v -> W26.get().mreeStatus(vxpName())),
                btn("Permissoes", 0xFF37474F, v -> W26.get().mreeSetPermission(vxpName(), 0xFFFFFFFF))));
        mre.addView(row(
                btn("Apagar (uninstall)", 0xFF37474F, v -> W26.get().mreeUninstall(vxpName())),
                btn("Excluir (delete)", WARN, v -> W26.get().mreeDelete(vxpName()))));
        mre.addView(row(
                btn("Desinstalar TODOS", WARN, v -> W26.get().mreeUninstallAll()),
                btn("Limpar testes", 0xFF37474F, v -> cleanTests())));
        ctrl.addView(mre);

        // ---- Sync & notificacoes ----
        LinearLayout sync = card();
        sync.addView(header("Sync & notificacoes"));
        sync.addView(row(
                btn("Hora", ACCENT, v -> W26.get().syncTime()),
                btn("Bateria", 0xFF37474F, v -> W26.get().battery()),
                btn("Encontrar", 0xFF37474F, v -> W26.get().findDevice())));
        sync.addView(btn("Notificacao teste", ACCENT, v -> W26.get().notifyWatch(
                "Qoder", "Teste do celular", "Se voce esta lendo isso, funcionou!")));
        sync.addView(btn("Acesso a notificacoes", 0xFF37474F, v -> startActivity(
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        ctrl.addView(sync);

        // ---- Console avancado ----
        LinearLayout adv = card();
        adv.addView(header("Console de protocolo"));
        tjdCmd = field("X1,LINK");
        adv.addView(labeled("TJD (sem $LHBT)", tjdCmd));
        adv.addView(btn("Enviar TJD", 0xFF37474F, v -> {
            String c = tjdCmd.getText().toString().trim();
            if (!c.isEmpty()) W26.get().sendTjd(c);
        }));
        mtkHex = field("SYNC REQV");
        adv.addView(labeled("MTK cru (hex ou texto)", mtkHex));
        adv.addView(row(
                btn("Enviar texto", 0xFF37474F, v -> {
                    String s = mtkHex.getText().toString().trim();
                    if (!s.isEmpty()) W26.get().sendMtkAuto(Mtk.cmd(s));
                }),
                btn("Enviar hex", 0xFF37474F, v -> sendHex())));
        ctrl.addView(adv);

        ScrollView ctrlScroll = new ScrollView(this);
        ctrlScroll.addView(ctrl);
        root.addView(ctrlScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f));

        LinearLayout logCard = card();
        logCard.addView(header("Log"));
        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTextColor(MUTED);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logScroll = new ScrollView(this);
        logScroll.addView(logView);
        logCard.addView(logScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        logCard.addView(btn("Limpar log", 0xFF37474F, v -> logView.setText("")));
        root.addView(logCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f));
        return root;
    }

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
            log("hex invalido: " + e.getMessage());
        }
    }

    private void refreshFinder() {
        if (finderView != null) finderView.setText(Finder.describe(this));
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
        bg.setCornerRadius(dp(12));
        c.setBackground(bg);
        c.setPadding(dp(14), dp(12), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView header(String text) {
        TextView t = new TextView(this);
        t.setText(text.toUpperCase());
        t.setTextSize(12);
        t.setTextColor(ACCENT);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    private EditText field(String text) {
        EditText e = new EditText(this);
        e.setText(text);
        e.setTextSize(13);
        e.setSingleLine(true);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF0F1419);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), 0xFF37474F);
        e.setBackground(bg);
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        return e;
    }

    private View labeled(String label, View field) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(11);
        t.setTextColor(MUTED);
        t.setPadding(0, dp(4), 0, dp(2));
        l.addView(t);
        l.addView(field);
        return l;
    }

    private Button btn(String text, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        b.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private View row(View... views) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (View v : views) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.topMargin = dp(6);
            lp.rightMargin = dp(4);
            r.addView(v, lp);
        }
        return r;
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
        stateView.setText(s);
        if ("conectado".equals(s)) {
            Finder.capture(this);
            refreshFinder();
        }
    }

    private void log(String line) {
        CharSequence old = logView.getText();
        String all = old.toString() + java.time.LocalTime.now().withNano(0) + "  " + line + "\n";
        if (all.length() > 24000) all = all.substring(all.length() - 20000);
        logView.setText(all);
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }
}