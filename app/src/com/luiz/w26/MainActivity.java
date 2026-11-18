package com.luiz.w26;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Bancada de testes do relogio W26: conexao BLE, notificacoes MTK 3.0 e console. */
public class MainActivity extends Activity implements W26.Listener {
    private static final String DEFAULT_MAC = "C2:BA:45:15:C8:F9";
    private static final String DEFAULT_CLASSIC = "06:87:E6:0C:62:61";
    private static final int REQ_VXP = 7;

    private TextView stateView;
    private TextView logView;
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
    }

    private View buildUi() {
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("W26 — bancada de testes");
        title.setTextSize(20);
        title.setTextColor(Color.BLACK);
        root.addView(title);

        stateView = new TextView(this);
        stateView.setText("desconectado");
        stateView.setTextSize(15);
        stateView.setTextColor(Color.parseColor("#1565C0"));
        root.addView(stateView);

        macView = new EditText(this);
        macView.setText(DEFAULT_MAC);
        macView.setTextSize(13);
        macView.setSingleLine(true);
        root.addView(macView);

        classicView = new EditText(this);
        classicView.setText(DEFAULT_CLASSIC);
        classicView.setTextSize(13);
        classicView.setSingleLine(true);
        root.addView(classicView);

        root.addView(row(
                button("Conectar", v -> W26.get().connect(this, macView.getText().toString().trim())),
                button("Desconectar", v -> W26.get().disconnect())));

        root.addView(row(
                button("Conectar SPP", v -> W26.get().connectSpp(this, classicView.getText().toString().trim())),
                button("SPP off", v -> W26.get().disconnectSpp())));

        root.addView(row(
                button("Hora", v -> W26.get().syncTime()),
                button("Bateria", v -> W26.get().battery()),
                button("Encontrar", v -> W26.get().findDevice())));

        root.addView(row(
                button("Notif. teste", v -> W26.get().notifyWatch(
                        "Qoder", "Teste do celular", "Se voce esta lendo isso, funcionou!"))));

        root.addView(row(
                button("Instalar VXP...", v -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, REQ_VXP);
                }),
                button("MREE listar", v -> W26.get().mreeList())));

        root.addView(row(
                button("Instalar face.vxp", v -> {
                    try {
                        W26.get().mreeInstall("face.vxp", asset("face.vxp"));
                    } catch (Exception e) {
                        log("asset: " + e.getMessage());
                    }
                }),
                button("MREE apagar", v -> W26.get().mreeUninstall(vxpName.getText().toString().trim()))));

        root.addView(row(
                button("MREE CTA", v -> W26.get().mreeCta()),
                button("Instalar LuizFace", v -> {
                    try {
                        W26.get().mreeInstall("luizface.vxp", asset("luizface.vxp"));
                    } catch (Exception e) {
                        log("asset: " + e.getMessage());
                    }
                })));

        root.addView(button("Limpar testes MRE (libera espaco)", v -> {
            for (String n : new String[]{"qoder.vxp", "qoder2.vxp", "qodertest.vxp", "face2.vxp", "luizface.vxp"})
                W26.get().mreeUninstall(n);
            log("limpeza: uninstall pedido para os vxp de teste");
        }));

        vxpName = new EditText(this);
        vxpName.setText("face.vxp");
        vxpName.setTextSize(13);
        vxpName.setSingleLine(true);
        root.addView(vxpName);

        root.addView(row(
                button("MREE status", v -> W26.get().mreeStatus(vxpName.getText().toString().trim())),
                button("MREE perms", v -> W26.get().mreePermissions(vxpName.getText().toString().trim())),
                button("MREE excluir", v -> W26.get().mreeDelete(vxpName.getText().toString().trim()))));

        root.addView(row(
                button("Acesso a notificacoes", v -> startActivity(
                        new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))));

        TextView t1 = new TextView(this);
        t1.setText("Comando TJD (sem $LHBT, ex: X0,GET,7)");
        t1.setTextSize(12);
        root.addView(t1);
        tjdCmd = new EditText(this);
        tjdCmd.setHint("X1,LINK");
        tjdCmd.setTextSize(13);
        tjdCmd.setSingleLine(true);
        root.addView(tjdCmd);
        root.addView(button("Enviar TJD", v -> {
            String c = tjdCmd.getText().toString().trim();
            if (!c.isEmpty()) W26.get().sendTjd(c);
        }));

        TextView t2 = new TextView(this);
        t2.setText("Frame MTK cru (hex, sem F0F0F0F1) ou texto plain");
        t2.setTextSize(12);
        root.addView(t2);
        mtkHex = new EditText(this);
        mtkHex.setHint("SYNC REQV");
        mtkHex.setTextSize(13);
        mtkHex.setSingleLine(true);
        root.addView(mtkHex);
        root.addView(row(
                button("Enviar texto", v -> {
                    String s = mtkHex.getText().toString().trim();
                    if (!s.isEmpty()) W26.get().sendMtkAuto(Mtk.cmd(s));
                }),
                button("Enviar hex", v -> {
                    String s = mtkHex.getText().toString().replace(" ", "").trim();
                    try {
                        byte[] b = new byte[s.length() / 2];
                        for (int i = 0; i < b.length; i++)
                            b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
                        W26.get().sendMtkAuto(b);
                    } catch (Exception e) {
                        log("hex invalido: " + e.getMessage());
                    }
                })));

        Button clear = button("Limpar log", v -> logView.setText(""));
        root.addView(clear);

        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTextColor(Color.DKGRAY);
        logScroll = new ScrollView(this);
        logScroll.addView(logView);
        root.addView(logScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private View row(View... views) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (View v : views) r.addView(v, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
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
            log("VXP " + name + " (" + vxp.length + " bytes) -> " + (char) vxp[0] + (char) vxp[1] + (char) vxp[2] + (char) vxp[3]);
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
    }

    private void log(String line) {
        CharSequence old = logView.getText();
        String all = old.toString() + java.time.LocalTime.now().withNano(0) + "  " + line + "\n";
        if (all.length() > 24000) all = all.substring(all.length() - 20000);
        logView.setText(all);
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }
}
