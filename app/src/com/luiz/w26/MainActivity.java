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
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

/** W26 Bancada — companion do relogio estilo Da Fit: dashboard + abas (dispositivo,
 *  mostradores, ajustes, console) e sincronizacao celular<->relogio. */
public class MainActivity extends Activity implements W26.Listener {
    private static final String DEFAULT_MAC = "C2:BA:45:15:C8:F9";
    private static final String DEFAULT_CLASSIC = "06:87:E6:0C:62:61";
    private static final int REQ_VXP = 7;

    private static final int BG = 0xFF080C12;
    private static final int CARD = 0xFF121A24;
    private static final int CARD2 = 0xFF0E151E;
    private static final int STROKE = 0xFF1E2A38;
    private static final int FIELD = 0xFF0B1119;
    private static final int GHOST = 0xFF1A2430;
    private static final int ACCENT = 0xFF3D7EFF;
    private static final int ACCENT2 = 0xFF1B2C50;
    private static final int GREEN = 0xFF2DD4A7;
    private static final int WARN = 0xFFF0616D;
    private static final int AMBER = 0xFFF5B942;
    private static final int TEXT = 0xFFEDF2F8;
    private static final int MUTED = 0xFF7E8CA0;
    private static final int DARK = 0xFF080C12;

    private static final int TAB_HOME = 0, TAB_DEVICE = 1, TAB_DIAL = 2, TAB_SETTINGS = 3,
            TAB_NOTIF = 4, TAB_CONSOLE = 5, NUM_TABS = 6;

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
    private Spinner facePreset;
    private TextView faceInfo;
    private Switch autoSyncSwitch;
    private Switch notifSwitch;
    private Switch skipSystemSwitch;
    private LinearLayout notifAppList;
    private boolean notifAppsBuilt;
    private Ring watchRing;

    private FrameLayout pages;
    private final View[] pageViews = new View[NUM_TABS];
    private final Icon[] tabIcons = new Icon[NUM_TABS];
    private final TextView[] tabLabels = new TextView[NUM_TABS];
    private final LinearLayout[] tabButtons = new LinearLayout[NUM_TABS];
    private int currentTab = -1;

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

    // ---- estrutura principal: header + paginas + barra de abas ----

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        root.addView(buildHeader());

        pages = new FrameLayout(this);
        pageViews[TAB_HOME] = pageHome();
        pageViews[TAB_DEVICE] = pageDevice();
        pageViews[TAB_DIAL] = pageDial();
        pageViews[TAB_SETTINGS] = pageSettings();
        pageViews[TAB_NOTIF] = pageNotif();
        pageViews[TAB_CONSOLE] = pageConsole();
        for (View v : pageViews) {
            v.setVisibility(View.GONE);
            pages.addView(v, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        root.addView(pages, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildTabBar());
        selectTab(TAB_HOME);
        return root;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(14), dp(16), dp(10));

        ImageView logo = icon(Icon.WATCH, ACCENT, 30);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(30), dp(30));
        llp.rightMargin = dp(10);
        header.addView(logo, llp);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("W26 Bancada");
        title.setTextSize(19);
        title.setTextColor(TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titles.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Watch 6 · companion");
        sub.setTextSize(11);
        sub.setTextColor(MUTED);
        titles.addView(sub);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        stateView = chip("desconectado", GHOST, MUTED);
        header.addView(stateView);
        return header;
    }

    private View buildTabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(CARD2);
        bar.setPadding(dp(6), dp(7), dp(6), dp(9));
        GradientDrawable top = new GradientDrawable();
        top.setColor(CARD2);
        bar.setBackground(top);
        // linha superior sutil
        View divider = new View(this);
        divider.setBackgroundColor(STROKE);

        String[] labels = {"Início", "Dispositivo", "Mostradores", "Ajustes", "Notif.", "Console"};
        int[] icons = {Icon.HOME, Icon.BT, Icon.DIAL, Icon.SLIDERS, Icon.BELL, Icon.TERMINAL};
        for (int i = 0; i < NUM_TABS; i++) {
            final int idx = i;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(2), dp(7), dp(2), dp(6));
            tab.setOnClickListener(v -> selectTab(idx));
            Icon ic = new Icon(icons[i], MUTED, getResources().getDisplayMetrics().density);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(ic);
            tab.addView(iv, new LinearLayout.LayoutParams(dp(23), dp(23)));
            TextView lab = new TextView(this);
            lab.setText(labels[i]);
            lab.setTextSize(10);
            lab.setTextColor(MUTED);
            lab.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = dp(3);
            tab.addView(lab, tlp);
            tabIcons[i] = ic;
            tabLabels[i] = lab;
            tabButtons[i] = tab;
            bar.addView(tab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        wrap.addView(bar);
        return wrap;
    }

    private void selectTab(int idx) {
        if (idx == currentTab) return;
        currentTab = idx;
        if (idx == TAB_NOTIF && !notifAppsBuilt) buildNotifApps();
        for (int i = 0; i < NUM_TABS; i++) {
            pageViews[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
            boolean sel = i == idx;
            tabIcons[i].setColor(sel ? ACCENT : MUTED);
            tabLabels[i].setTextColor(sel ? ACCENT : MUTED);
            GradientDrawable g = new GradientDrawable();
            g.setColor(sel ? 0x1A3D7EFF : Color.TRANSPARENT);
            g.setCornerRadius(dp(14));
            tabButtons[i].setBackground(g);
        }
    }

    // ---- pagina: INICIO (dashboard) ----

    private View pageHome() {
        LinearLayout c = pageContainer();

        // Hero card com gradiente + anel de bateria
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF3D7EFF, 0xFF16233F});
        hg.setCornerRadius(dp(22));
        hero.setBackground(hg);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = dp(14);
        hero.setLayoutParams(hlp);

        LinearLayout hinfo = new LinearLayout(this);
        hinfo.setOrientation(LinearLayout.VERTICAL);
        TextView hname = new TextView(this);
        hname.setText("Watch 6");
        hname.setTextSize(24);
        hname.setTextColor(0xFFFFFFFF);
        hname.setTypeface(Typeface.DEFAULT_BOLD);
        hinfo.addView(hname);
        TextView hmodel = new TextView(this);
        hmodel.setText("W26 · MRE 2.0 · 176×220");
        hmodel.setTextSize(11);
        hmodel.setTextColor(0xCCFFFFFF);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.bottomMargin = dp(10);
        hinfo.addView(hmodel, mlp);
        watchStateView = chip("desconectado", 0x33FFFFFF, 0xFFFFFFFF);
        hinfo.addView(watchStateView);
        hero.addView(hinfo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout ringWrap = new FrameLayout(this);
        watchRing = new Ring(0x33FFFFFF, 0xFFFFFFFF, getResources().getDisplayMetrics().density);
        ringWrap.setBackground(watchRing);
        watchBattView = new TextView(this);
        watchBattView.setText("—");
        watchBattView.setTextSize(21);
        watchBattView.setTextColor(0xFFFFFFFF);
        watchBattView.setTypeface(Typeface.DEFAULT_BOLD);
        watchBattView.setGravity(Gravity.CENTER);
        ringWrap.addView(watchBattView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView ringLab = new TextView(this);
        ringLab.setText("bateria");
        ringLab.setTextSize(9);
        ringLab.setTextColor(0xCCFFFFFF);
        ringLab.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams rllp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rllp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        rllp.bottomMargin = dp(14);
        ringWrap.addView(ringLab, rllp);
        hero.addView(ringWrap, new LinearLayout.LayoutParams(dp(94), dp(94)));
        c.addView(hero);

        // Acoes rapidas (grade 2x2)
        c.addView(sectionTitle("Ações rápidas"));
        c.addView(row(
                quickTile(Icon.SYNC, "Sincronizar", v -> {
                    W26.get().syncTime();
                    W26.get().battery();
                    Prefs.markSync(this);
                    refreshStatus();
                    log("sync: hora + bateria pedidos");
                }),
                quickTile(Icon.PIN, "Achar relógio", v -> W26.get().alertWatch(2))));
        c.addView(row(
                quickTile(Icon.BELL, "Achar celular", v -> alertPhone()),
                quickTile(Icon.MAP, "Enviar clima", v -> Weather.fetchAndPush(this))));

        // Estatisticas
        c.addView(sectionTitle("Visão geral"));
        phoneBattView = statValue("—");
        lastSyncView = statValue("—");
        gpsView = statValue("—");
        notifView = statValue("—");
        c.addView(row(statTile("Bateria celular", phoneBattView), statTile("Última sync", lastSyncView)));
        c.addView(row(statTile("GPS", gpsView), statTile("Notificações", notifView)));

        // Atalhos
        c.addView(linkCard(Icon.DIAL, "Mostrador digital", "Fundos prontos + estatísticas nativas", TAB_DIAL));
        c.addView(linkCard(Icon.BELL, "Notificações", "Escolha os apps e corte carga/mídia", TAB_NOTIF));

        return scroll(c);
    }

    // ---- pagina: DISPOSITIVO (conexao) ----

    private View pageDevice() {
        LinearLayout c = pageContainer();

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
                ghostBtn("Desconectar BLE", v -> W26.get().disconnect()),
                ghostBtn("Desconectar SPP", v -> W26.get().disconnectSpp())));
        c.addView(conn);

        LinearLayout info = card();
        info.addView(sectionTitle("Sobre o dispositivo"));
        info.addView(infoRow("Modelo", "Watch 6 / W26 (clone MTK)"));
        info.addView(infoRow("Firmware", "W26_B_BL6133_201128_1"));
        info.addView(infoRow("Tela", "176 × 220"));
        info.addView(infoRow("Plataforma", "MRE 2.0"));
        info.addView(infoRow("BLE MAC", DEFAULT_MAC));
        info.addView(infoRow("SPP MAC", DEFAULT_CLASSIC));
        c.addView(info);

        LinearLayout tip = card();
        tip.addView(sectionTitle("Como conectar"));
        TextView t = new TextView(this);
        t.setText("1. Pareie o relógio no Bluetooth do celular.\n"
                + "2. Toque em “Conectar BLE” para notificações, hora, bateria e finder.\n"
                + "3. Toque em “Conectar SPP” para instalar mostradores/apps (MREE).\n"
                + "Os dois canais podem ficar ativos ao mesmo tempo.");
        t.setTextSize(12);
        t.setTextColor(MUTED);
        t.setLineSpacing(dp(3), 1f);
        tip.addView(t);
        c.addView(tip);

        return scroll(c);
    }

    // ---- pagina: MOSTRADORES ----

    private View pageDial() {
        LinearLayout c = pageContainer();

        LinearLayout face = card();
        face.addView(sectionTitle("Criar mostrador digital"));
        facePreset = new Spinner(this);
        String[] names = new String[FaceBuilder.PRESETS.length];
        for (int i = 0; i < names.length; i++) names[i] = FaceBuilder.PRESETS[i].name;
        ArrayAdapter<String> pa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        pa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        facePreset.setAdapter(pa);
        face.addView(labeled("Fundo", facePreset));
        faceInfo = new TextView(this);
        faceInfo.setText("Digital com horas + estatísticas (passos/HR/data) e atalhos de toque "
                + "(música/HR) nativos do relógio. Os dois fundos já vêm no template; o relógio "
                + "alterna entre eles pelo toque.");
        faceInfo.setTextSize(12);
        faceInfo.setTextColor(MUTED);
        faceInfo.setLineSpacing(dp(2), 1f);
        faceInfo.setPadding(0, dp(6), 0, dp(4));
        face.addView(faceInfo);
        face.addView(primaryBtn("Gerar e instalar no relógio", v -> installFace()));
        c.addView(face);

        LinearLayout mre = card();
        mre.addView(sectionTitle("Mostradores & apps (MRE / SPP)"));
        mre.addView(row(
                ghostBtn("LuizFace RES", v -> installAsset("luizface3.vxp", "luizface3.vxp")),
                ghostBtn("Face REF", v -> installAsset("ref.vxp", "refface.vxp"))));
        mre.addView(row(
                ghostBtn("LuizFace", v -> installAsset("luizface.vxp", "luizface.vxp")),
                ghostBtn("LuizApp", v -> installAsset("luizapp.vxp", "luizapp.vxp"))));
        mre.addView(ghostBtn("Ronman digital (template)", v -> installAsset("ronman.vxp", "ronman.vxp")));
        mre.addView(row(
                primaryBtn("Instalar VXP…", v -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, REQ_VXP);
                }),
                ghostBtn("Listar (MREE)", v -> W26.get().mreeList())));
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
        c.addView(mre);

        return scroll(c);
    }

    // ---- pagina: AJUSTES (sync + finder) ----

    private View pageSettings() {
        LinearLayout c = pageContainer();

        LinearLayout sync = card();
        sync.addView(sectionTitle("Sincronização"));
        autoSyncSwitch = makeSwitch(Prefs.autoSync(this));
        autoSyncSwitch.setOnCheckedChangeListener((b, checked) -> Prefs.setAutoSync(this, checked));
        sync.addView(toggleRow("Hora automática ao conectar", autoSyncSwitch));
        sync.addView(row(
                primaryBtn("Sincronizar agora", v -> {
                    W26.get().syncTime();
                    W26.get().battery();
                    Prefs.markSync(this);
                    refreshStatus();
                    log("sync: hora + bateria pedidos");
                }),
                ghostBtn("Bateria do relógio", v -> W26.get().battery())));
        sync.addView(row(
                ghostBtn("Testar alerta celular", v -> alertPhone()),
                ghostBtn("Enviar clima", v -> Weather.fetchAndPush(this))));
        c.addView(sync);

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
        c.addView(find);

        return scroll(c);
    }

    // ---- pagina: NOTIFICACOES (filtro por app) ----

    private View pageNotif() {
        LinearLayout c = pageContainer();

        LinearLayout f = card();
        f.addView(sectionTitle("Notificações do celular"));
        notifSwitch = makeSwitch(Prefs.notifEnabled(this));
        notifSwitch.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setNotifEnabled(this, checked);
            refreshStatus();
        });
        f.addView(toggleRow("Encaminhar notificações", notifSwitch));
        skipSystemSwitch = makeSwitch(Prefs.notifSkipSystem(this));
        skipSystemSwitch.setOnCheckedChangeListener((b, checked) -> Prefs.setNotifSkipSystem(this, checked));
        f.addView(toggleRow("Ignorar carga, mídia e progresso", skipSystemSwitch));
        notifAccessView = new TextView(this);
        notifAccessView.setTextSize(11);
        notifAccessView.setTextColor(MUTED);
        notifAccessView.setPadding(0, dp(4), 0, dp(6));
        f.addView(notifAccessView);
        f.addView(row(
                ghostBtn("Acesso notificações", v -> startActivity(
                        new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))),
                ghostBtn("Notificação teste", v -> W26.get().notifyWatch(
                        "W26", "Teste do celular", "Se você está lendo isso, funcionou!"))));
        c.addView(f);

        LinearLayout apps = card();
        apps.addView(sectionTitle("Apps que o relógio lê"));
        TextView note = new TextView(this);
        note.setText("Desligue os apps que não devem vibrar no relógio. O filtro acima já corta "
                + "avisos de carga, mídia (play/pause) e progresso do sistema.");
        note.setTextSize(12);
        note.setTextColor(MUTED);
        note.setLineSpacing(dp(2), 1f);
        note.setPadding(0, 0, 0, dp(8));
        apps.addView(note);
        notifAppList = new LinearLayout(this);
        notifAppList.setOrientation(LinearLayout.VERTICAL);
        apps.addView(notifAppList);
        c.addView(apps);

        return scroll(c);
    }

    private void buildNotifApps() {
        notifAppsBuilt = true;
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        java.util.Map<String, android.content.pm.ApplicationInfo> map = new java.util.HashMap<>();
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            android.content.pm.ApplicationInfo ai = ri.activityInfo.applicationInfo;
            if (ai.packageName.equals(getPackageName())) continue;
            map.put(ai.packageName, ai);
        }
        java.util.List<android.content.pm.ApplicationInfo> list = new java.util.ArrayList<>(map.values());
        java.util.Collections.sort(list, (a, b) -> label(pm, a).compareToIgnoreCase(label(pm, b)));
        java.util.Set<String> blocked = Prefs.notifBlocked(this);
        for (android.content.pm.ApplicationInfo ai : list)
            notifAppList.addView(appRow(pm, ai, !blocked.contains(ai.packageName)));
    }

    private String label(PackageManager pm, android.content.pm.ApplicationInfo ai) {
        try {
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            return ai.packageName;
        }
    }

    private View appRow(PackageManager pm, android.content.pm.ApplicationInfo ai, boolean allowed) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(7), 0, dp(7));
        ImageView iv = new ImageView(this);
        try {
            iv.setImageDrawable(pm.getApplicationIcon(ai));
        } catch (Exception e) {
            iv.setImageDrawable(new Icon(Icon.PLUS, MUTED, getResources().getDisplayMetrics().density));
        }
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(34), dp(34));
        ilp.rightMargin = dp(12);
        r.addView(iv, ilp);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(label(pm, ai));
        name.setTextSize(14);
        name.setTextColor(TEXT);
        name.setSingleLine(true);
        t.addView(name);
        TextView pkg = new TextView(this);
        pkg.setText(ai.packageName);
        pkg.setTextSize(10);
        pkg.setTextColor(MUTED);
        pkg.setSingleLine(true);
        t.addView(pkg);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = makeSwitch(allowed);
        sw.setOnCheckedChangeListener((b, checked) -> Prefs.setNotifBlocked(this, ai.packageName, !checked));
        r.addView(sw);
        return r;
    }

    // ---- pagina: CONSOLE (protocolo + log) ----

    private View pageConsole() {
        LinearLayout c = pageContainer();

        LinearLayout adv = card();
        adv.addView(sectionTitle("Console de protocolo"));
        tjdCmd = field("X1,LINK");
        adv.addView(labeled("TJD (sem $LHBT)", tjdCmd));
        adv.addView(ghostBtn("Enviar TJD", v -> {
            String cmd = tjdCmd.getText().toString().trim();
            if (!cmd.isEmpty()) W26.get().sendTjd(cmd);
        }));
        mtkHex = field("SYNC REQV");
        adv.addView(labeled("MTK cru (hex ou texto)", mtkHex));
        adv.addView(row(
                ghostBtn("Enviar texto", v -> {
                    String s = mtkHex.getText().toString().trim();
                    if (!s.isEmpty()) W26.get().sendMtkAuto(Mtk.cmd(s));
                }),
                ghostBtn("Enviar hex", v -> sendHex())));
        c.addView(adv);

        LinearLayout logCard = card();
        logCard.addView(sectionTitle("Log"));
        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTextColor(MUTED);
        logView.setTypeface(Typeface.MONOSPACE);
        logScroll = new ScrollView(this);
        logScroll.addView(logView);
        logCard.addView(logScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));
        logCard.addView(ghostBtn("Limpar log", v -> logView.setText("")));
        c.addView(logCard);

        return scroll(c);
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

    private void installFace() {
        FaceBuilder.Preset p = FaceBuilder.PRESETS[facePreset.getSelectedItemPosition()];
        try {
            byte[] v = FaceBuilder.build(asset(p.asset));
            String name = "lz_" + p.asset.substring(p.asset.indexOf('/') + 1);
            // O relogio tem pouco espaco para mostradores: libera os presets antes
            // de instalar o novo (senao o instalador recusa com -1000 / "sem espaco").
            for (FaceBuilder.Preset o : FaceBuilder.PRESETS)
                W26.get().mreeUninstall("lz_" + o.asset.substring(o.asset.indexOf('/') + 1));
            W26.get().mreeInstall(name, v);
            log("mostrador \"" + p.name + "\" (" + v.length + " B) enviado como " + name);
        } catch (Exception e) {
            log("criar mostrador: " + e);
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
        if (watchRing != null) watchRing.setPercent(wb < 0 ? 0 : wb);
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
        if (watchRing != null) watchRing.setPercent(percent);
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

    private LinearLayout pageContainer() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(4), dp(16), dp(18));
        return c;
    }

    private ScrollView scroll(View content) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setVerticalScrollBarEnabled(false);
        s.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    private ImageView icon(int kind, int color, int sizeDp) {
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new Icon(kind, color, getResources().getDisplayMetrics().density));
        return iv;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), STROKE);
        c.setBackground(bg);
        c.setPadding(dp(17), dp(15), dp(17), dp(17));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(13);
        c.setLayoutParams(lp);
        return c;
    }

    private View quickTile(int iconKind, String label, View.OnClickListener l) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), STROKE);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x223D7EFF), bg, null));
        t.setPadding(dp(8), dp(16), dp(8), dp(14));
        t.setOnClickListener(l);
        t.addView(icon(iconKind, ACCENT, 26), new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextSize(12);
        lab.setTextColor(TEXT);
        lab.setTypeface(Typeface.DEFAULT_BOLD);
        lab.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(8);
        t.addView(lab, llp);
        return t;
    }

    private View linkCard(int iconKind, String title, String sub, int tab) {
        LinearLayout p = card();
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(30), dp(30));
        ilp.rightMargin = dp(14);
        p.addView(icon(iconKind, ACCENT, 30), ilp);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextSize(15);
        t1.setTextColor(TEXT);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        t.addView(t1);
        TextView t2 = new TextView(this);
        t2.setText(sub);
        t2.setTextSize(11);
        t2.setTextColor(MUTED);
        t.addView(t2);
        p.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        p.addView(icon(Icon.CHEVRON, MUTED, 20), new LinearLayout.LayoutParams(dp(20), dp(20)));
        p.setOnClickListener(v -> selectTab(tab));
        return p;
    }

    private View statTile(String label, TextView value) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD2);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), STROKE);
        t.setBackground(bg);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextSize(11);
        lab.setTextColor(MUTED);
        t.addView(lab);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = dp(3);
        t.addView(value, vlp);
        return t;
    }

    private View infoRow(String label, String value) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(5), 0, dp(5));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(13);
        l.setTextColor(MUTED);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(13);
        v.setTextColor(TEXT);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.END);
        r.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        return r;
    }

    private TextView sectionTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(2), 0, dp(10));
        return t;
    }

    private TextView statValue(String v) {
        TextView t = new TextView(this);
        t.setText(v);
        t.setTextSize(17);
        t.setTextColor(TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView chip(String text, int bg, int fg) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11);
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
        g.setCornerRadius(dp(14));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), g, null));
        b.setPadding(dp(12), dp(12), dp(12), dp(12));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(7);
        b.setLayoutParams(lp);
        return b;
    }

    private Button primaryBtn(String text, View.OnClickListener l) {
        return btnBase(text, ACCENT, 0xFFFFFFFF, l);
    }

    private Button ghostBtn(String text, View.OnClickListener l) {
        return btnBase(text, GHOST, TEXT, l);
    }

    private Button dangerBtn(String text, View.OnClickListener l) {
        return btnBase(text, 0x33F0616D, WARN, l);
    }

    private View row(View... views) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < views.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.topMargin = dp(7);
            if (i < views.length - 1) lp.rightMargin = dp(8);
            r.addView(views[i], lp);
        }
        return r;
    }

    private Switch makeSwitch(boolean checked) {
        Switch s = new Switch(this);
        s.setChecked(checked);
        s.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
        s.setTrackTintList(ColorStateList.valueOf(ACCENT));
        return s;
    }

    private View toggleRow(String label, Switch sw) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14);
        t.setTextColor(TEXT);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(sw);
        r.setPadding(0, dp(5), 0, dp(5));
        return r;
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
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), STROKE);
        e.setBackground(bg);
        e.setPadding(dp(13), dp(10), dp(13), dp(10));
        return e;
    }

    private View labeled(String label, View field) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(11);
        t.setTextColor(MUTED);
        t.setPadding(0, dp(7), 0, dp(4));
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
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        if (req != REQ_VXP) return;
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
        g.setColor(up ? 0x222DD4A7 : GHOST);
        g.setCornerRadius(dp(20));
        stateView.setBackground(g);
        stateView.setTextColor(up ? GREEN : MUTED);
        watchStateView.setText(up ? "conectado" : s);
        GradientDrawable wg = new GradientDrawable();
        wg.setColor(up ? 0x332DD4A7 : 0x33FFFFFF);
        wg.setCornerRadius(dp(20));
        watchStateView.setBackground(wg);
        watchStateView.setTextColor(0xFFFFFFFF);
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