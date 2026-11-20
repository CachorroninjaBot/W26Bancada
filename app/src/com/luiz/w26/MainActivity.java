package com.luiz.w26;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** W26 Bancada — companion do relogio: dashboard, saude (BPM/passos/sono/SpO2/temp),
 *  dispositivo + apps MRE, mostradores, ajustes e console com log persistente + API. */
public class MainActivity extends Ui implements W26.Listener, Health.Callback, Apps.Callback {
    private static final int REQ_VXP = 7;
    private static final int TAB_HOME = 0, TAB_HEALTH = 1, TAB_DEVICE = 2, TAB_DIAL = 3,
            TAB_SETTINGS = 4, TAB_CONSOLE = 5, NUM_TABS = 6;

    private TextView stateView, watchStateView, watchBattView, phoneBattView, lastSyncView, gpsView, notifView;
    private TextView homeSteps, homeHr;
    private Ring watchRing;
    private TextView hSteps, hHr, hSleep, hCal, hSpo2, hTemp, hBp, hDistance;
    private Chart stepsChart, hrChart;
    private LinearLayout deviceInfoBox, appsBox;
    private TextView appsCountView, bleChip, sppChip;
    private EditText macView, classicView, tjdCmd, mtkHex, vxpName;
    private Spinner facePreset, logDay;
    private Switch autoSyncSwitch, autoConnectSwitch, healthAutoSwitch, notifSwitch, skipSystemSwitch, apiSwitch;
    private TextView notifAccessView, finderView, apiStatusView;
    private LinearLayout notifAppList;
    private boolean notifAppsBuilt;
    private TextView logView;
    private ScrollView logScroll;
    private final StringBuilder liveLog = new StringBuilder();
    private boolean logLive = true;

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
            refreshStatus();
            if (currentTab == TAB_HEALTH) refreshHealth();
            if (currentTab == TAB_DEVICE) refreshDevice();
            if (currentTab == TAB_CONSOLE && logLive) refreshLog();
            ui.postDelayed(this, 5000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogStore.init(this);
        Health.get().init(this);
        Apps.get().init();
        W26.get().addListener(this);
        Health.get().addCallback(this);
        Apps.get().addCallback(this);
        createChannel();
        setContentView(buildUi());
        requestPermissions();
        LinkSvc.start(this);
        refreshFinder();
        refreshStatus();
        refreshHealth();
        refreshDevice();
        refreshApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFinder();
        refreshStatus();
        refreshHealth();
        ui.removeCallbacks(periodic);
        ui.postDelayed(periodic, 5000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(periodic);
    }

    @Override
    protected void onDestroy() {
        W26.get().removeListener(this);
        Health.get().removeCallback(this);
        Apps.get().removeCallback(this);
        super.onDestroy();
    }

    // ---- estrutura ----

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        // targetSdk 36: Android 15+ forca edge-to-edge; respeita barra de status/navegacao.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.addView(buildHeader());
        pages = new FrameLayout(this);
        pageViews[TAB_HOME] = pageHome();
        pageViews[TAB_HEALTH] = pageHealth();
        pageViews[TAB_DEVICE] = pageDevice();
        pageViews[TAB_DIAL] = pageDial();
        pageViews[TAB_SETTINGS] = pageSettings();
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
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(30), dp(30));
        llp.rightMargin = dp(10);
        header.addView(icon(Icon.WATCH, ACCENT, 30), llp);
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
        bar.setPadding(dp(4), dp(7), dp(4), dp(9));
        View divider = new View(this);
        divider.setBackgroundColor(STROKE);
        String[] labels = {"Início", "Saúde", "Dispositivo", "Mostradores", "Ajustes", "Console"};
        int[] icons = {Icon.HOME, Icon.HEART, Icon.BT, Icon.DIAL, Icon.SLIDERS, Icon.TERMINAL};
        for (int i = 0; i < NUM_TABS; i++) {
            final int idx = i;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(1), dp(7), dp(1), dp(6));
            tab.setOnClickListener(v -> selectTab(idx));
            Icon ic = new Icon(icons[i], MUTED, density());
            android.widget.ImageView iv = new android.widget.ImageView(this);
            iv.setImageDrawable(ic);
            tab.addView(iv, new LinearLayout.LayoutParams(dp(22), dp(22)));
            TextView lab = new TextView(this);
            lab.setText(labels[i]);
            lab.setTextSize(9);
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
        if (idx == TAB_SETTINGS && !notifAppsBuilt) buildNotifApps();
        if (idx == TAB_HEALTH) refreshHealth();
        if (idx == TAB_DEVICE) {
            refreshDevice();
            refreshApps();
        }
        if (idx == TAB_CONSOLE) refreshLog();
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

    // ---- INICIO ----

    private View pageHome() {
        LinearLayout c = pageContainer();
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
        watchRing = new Ring(0x33FFFFFF, 0xFFFFFFFF, density());
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

        c.addView(sectionTitle("Ações rápidas"));
        c.addView(row(
                quickTile(Icon.SYNC, "Sincronizar", v -> doSync()),
                quickTile(Icon.PIN, "Achar relógio", v -> W26.get().alertWatch(2))));
        c.addView(row(
                quickTile(Icon.BELL, "Achar celular", v -> alertPhone()),
                quickTile(Icon.MAP, "Clima no relógio", v -> Weather.fetchAndPush(this))));

        c.addView(sectionTitle("Visão geral"));
        homeSteps = statValue("—");
        homeHr = statValue("—");
        phoneBattView = statValue("—");
        lastSyncView = statValue("—");
        gpsView = statValue("—");
        notifView = statValue("—");
        c.addView(row(statTile("Passos hoje", homeSteps), statTile("BPM", homeHr)));
        c.addView(row(statTile("Bateria celular", phoneBattView), statTile("Última sync", lastSyncView)));
        c.addView(row(statTile("GPS", gpsView), statTile("Notificações", notifView)));

        c.addView(linkCard(Icon.HEART, "Saúde", "BPM, passos, sono, SpO2 e temperatura", v -> selectTab(TAB_HEALTH)));
        c.addView(linkCard(Icon.DIAL, "Mostrador digital", "Fundos prontos + estatísticas nativas", v -> selectTab(TAB_DIAL)));
        return scroll(c);
    }

    // ---- SAUDE ----

    private View pageHealth() {
        LinearLayout c = pageContainer();
        LinearLayout today = card();
        today.addView(sectionTitle("Hoje"));
        hSteps = statValue("—");
        hHr = statValue("—");
        hSleep = statValue("—");
        hCal = statValue("—");
        hSpo2 = statValue("—");
        hTemp = statValue("—");
        hBp = statValue("—");
        hDistance = statValue("—");
        today.addView(row(
                healthTile(Icon.STEPS, ACCENT, "Passos", hSteps, "passos"),
                healthTile(Icon.HEART, WARN, "BPM", hHr, "bpm médio")));
        today.addView(row(
                healthTile(Icon.MOON, PURPLE, "Sono", hSleep, "horas"),
                healthTile(Icon.FLAME, AMBER, "Calorias", hCal, "kcal")));
        today.addView(row(
                healthTile(Icon.DROP, ACCENT, "SpO2", hSpo2, "% oxigênio"),
                healthTile(Icon.TEMP, GREEN, "Temperatura", hTemp, "°C corporal")));
        today.addView(row(
                healthTile(Icon.BP, WARN, "Pressão", hBp, "mmHg"),
                healthTile(Icon.MAP, GREEN, "Distância", hDistance, "km")));
        c.addView(today);

        LinearLayout measure = card();
        measure.addView(sectionTitle("Medir agora no relógio"));
        measure.addView(note("O relógio mede na hora e envia o resultado. Fique parado durante a medição."));
        measure.addView(row(
                primaryBtn("BPM", v -> Health.get().measure("hr")),
                ghostBtn("Pressão", v -> Health.get().measure("bp"))));
        measure.addView(row(
                ghostBtn("SpO2", v -> Health.get().measure("spo2")),
                ghostBtn("Temperatura", v -> Health.get().measure("temp"))));
        c.addView(measure);

        LinearLayout stepsCard = card();
        stepsCard.addView(sectionTitle("Passos — 7 dias"));
        stepsChart = new Chart(this, density());
        stepsCard.addView(stepsChart, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
        c.addView(stepsCard);

        LinearLayout hrCard = card();
        hrCard.addView(sectionTitle("BPM — 7 dias"));
        hrChart = new Chart(this, density());
        hrCard.addView(hrChart, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
        c.addView(hrCard);

        LinearLayout sync = card();
        sync.addView(sectionTitle("Sincronização de saúde"));
        healthAutoSwitch = makeSwitch(Prefs.healthAuto(this));
        healthAutoSwitch.setOnCheckedChangeListener((b, checked) -> Prefs.setHealthAuto(this, checked));
        sync.addView(toggleRow("Sincronizar ao conectar e periodicamente", healthAutoSwitch));
        sync.addView(primaryBtn("Sincronizar saúde agora", v -> Health.get().syncAll()));
        sync.addView(note("Dados vêm do relógio via Bluetooth (passos/sono/BPM/SpO2/temperatura). "
                + "O histórico fica salvo neste celular por dia."));
        c.addView(sync);
        return scroll(c);
    }

    // ---- DISPOSITIVO ----

    private View pageDevice() {
        LinearLayout c = pageContainer();
        LinearLayout conn = card();
        conn.addView(sectionTitle("Conexão"));
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        bleChip = chip("BLE: —", GHOST, MUTED);
        sppChip = chip("SPP: —", GHOST, MUTED);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.rightMargin = dp(8);
        chips.addView(bleChip, clp);
        chips.addView(sppChip);
        conn.addView(chips);
        macView = field(Prefs.bleMac(this));
        classicView = field(Prefs.sppMac(this));
        conn.addView(labeled("BLE (GATT)", macView));
        conn.addView(labeled("Clássico (SPP)", classicView));
        conn.addView(row(
                primaryBtn("Conectar BLE", v -> {
                    saveMacs();
                    Prefs.setWantConn(this, true);
                    LinkSvc.start(this);
                    W26.get().connect(this, macView.getText().toString().trim());
                }),
                primaryBtn("Conectar SPP", v -> {
                    saveMacs();
                    Prefs.setWantConn(this, true);
                    LinkSvc.start(this);
                    W26.get().connectSpp(this, classicView.getText().toString().trim());
                })));
        conn.addView(ghostBtn("Desconectar tudo", v -> {
            Prefs.setWantConn(this, false);
            W26.get().disconnect();
            W26.get().disconnectSpp();
        }));
        autoConnectSwitch = makeSwitch(Prefs.autoConnect(this));
        autoConnectSwitch.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setAutoConnect(this, checked);
            if (checked) LinkSvc.start(this);
        });
        conn.addView(toggleRow("Reconectar sozinho (BLE + SPP)", autoConnectSwitch));
        conn.addView(note("Com a reconexão automática, o app conecta BLE e SPP sozinho quando o "
                + "relógio aparece no Bluetooth, e mantém o link mesmo com o app fechado."));
        c.addView(conn);

        LinearLayout info = card();
        info.addView(sectionTitle("Sobre o relógio"));
        deviceInfoBox = new LinearLayout(this);
        deviceInfoBox.setOrientation(LinearLayout.VERTICAL);
        info.addView(deviceInfoBox);
        info.addView(ghostBtn("Atualizar informações", v -> {
            W26.get().requestDeviceInfo();
            W26.get().sendTjd("X0,GET,0");
            W26.get().sendTjd("X0,GET,5");
            W26.get().battery();
            ui.postDelayed(this::refreshDevice, 1500);
        }));
        c.addView(info);

        LinearLayout apps = card();
        apps.addView(sectionTitle("Apps & espaço"));
        appsCountView = new TextView(this);
        appsCountView.setTextSize(13);
        appsCountView.setTextColor(TEXT);
        appsCountView.setPadding(0, 0, 0, dp(6));
        apps.addView(appsCountView);
        apps.addView(note("Apps de fábrica não podem ser removidos. “Liberar espaço” desinstala "
                + "só os mostradores/apps que você instalou."));
        apps.addView(row(
                primaryBtn("Atualizar lista", v -> Apps.get().refresh()),
                dangerBtn("Liberar espaço", v -> {
                    Apps.get().freeSpace();
                    ui.postDelayed(this::refreshApps, 2500);
                })));
        appsBox = new LinearLayout(this);
        appsBox.setOrientation(LinearLayout.VERTICAL);
        apps.addView(appsBox);
        c.addView(apps);
        return scroll(c);
    }

    private void saveMacs() {
        Prefs.setMacs(this, macView.getText().toString().trim(), classicView.getText().toString().trim());
    }

    // ---- MOSTRADORES ----

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
        face.addView(note("Digital com horas + estatísticas (passos/HR/data) e atalhos de toque "
                + "(música/HR) nativos do relógio. O relógio alterna entre os dois fundos pelo toque."));
        face.addView(primaryBtn("Gerar e instalar no relógio", v -> installFace()));
        c.addView(face);

        LinearLayout mre = card();
        mre.addView(sectionTitle("Mostradores & apps de teste (MRE / SPP)"));
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

    // ---- AJUSTES ----

    private View pageSettings() {
        LinearLayout c = pageContainer();
        LinearLayout sync = card();
        sync.addView(sectionTitle("Sincronização"));
        autoSyncSwitch = makeSwitch(Prefs.autoSync(this));
        autoSyncSwitch.setOnCheckedChangeListener((b, checked) -> Prefs.setAutoSync(this, checked));
        sync.addView(toggleRow("Hora automática ao conectar", autoSyncSwitch));
        sync.addView(row(
                primaryBtn("Sincronizar agora", v -> doSync()),
                ghostBtn("Bateria do relógio", v -> W26.get().battery())));
        c.addView(sync);

        LinearLayout weather = card();
        weather.addView(sectionTitle("Clima no relógio"));
        weather.addView(note("“Enviar clima” manda a previsão de 6 dias para a tela de Clima do "
                + "próprio relógio (ícone, mín/máx por dia). Usa o GPS do celular para achar sua "
                + "cidade e o serviço grátis Open-Meteo. Não é um widget do celular."));
        weather.addView(row(
                primaryBtn("Enviar clima", v -> Weather.fetchAndPush(this)),
                ghostBtn("Atualizar GPS", v -> {
                    Finder.capture(this);
                    refreshFinder();
                    refreshStatus();
                })));
        c.addView(weather);

        LinearLayout find = card();
        find.addView(sectionTitle("Finder / localizador"));
        find.addView(note("O relógio não tem GPS: guardamos a última posição do celular e fazemos o relógio vibrar."));
        finderView = new TextView(this);
        finderView.setTextSize(13);
        finderView.setTextColor(TEXT);
        finderView.setPadding(0, 0, 0, dp(8));
        find.addView(finderView);
        find.addView(row(
                primaryBtn("Fazer vibrar", v -> W26.get().alertWatch(2)),
                ghostBtn("Abrir no mapa", v -> {
                    if (!Finder.hasFix(this)) {
                        log("finder: sem posição guardada");
                        return;
                    }
                    try {
                        startActivity(Finder.mapsIntent(this));
                    } catch (Exception e) {
                        log("finder: sem app de mapa (" + e.getMessage() + ")");
                    }
                })));
        c.addView(find);

        LinearLayout notif = card();
        notif.addView(sectionTitle("Notificações do celular"));
        notifSwitch = makeSwitch(Prefs.notifEnabled(this));
        notifSwitch.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setNotifEnabled(this, checked);
            refreshStatus();
        });
        notif.addView(toggleRow("Encaminhar notificações", notifSwitch));
        skipSystemSwitch = makeSwitch(Prefs.notifSkipSystem(this));
        skipSystemSwitch.setOnCheckedChangeListener((b, checked) -> Prefs.setNotifSkipSystem(this, checked));
        notif.addView(toggleRow("Ignorar carga, mídia e progresso", skipSystemSwitch));
        notifAccessView = new TextView(this);
        notifAccessView.setTextSize(11);
        notifAccessView.setTextColor(MUTED);
        notifAccessView.setPadding(0, dp(4), 0, dp(6));
        notif.addView(notifAccessView);
        notif.addView(row(
                ghostBtn("Acesso notificações", v -> startActivity(
                        new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))),
                ghostBtn("Notificação teste", v -> W26.get().notifyWatch(
                        "W26", "Teste do celular", "Se você está lendo isso, funcionou!"))));
        notifAppList = new LinearLayout(this);
        notifAppList.setOrientation(LinearLayout.VERTICAL);
        notif.addView(notifAppList);
        c.addView(notif);

        LinearLayout api = card();
        api.addView(sectionTitle("API interna (diagnóstico)"));
        apiSwitch = makeSwitch(Prefs.apiEnabled(this));
        apiSwitch.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setApiEnabled(this, checked);
            LinkSvc.applyApi(this);
            refreshApiStatus();
        });
        api.addView(toggleRow("Servidor HTTP em 127.0.0.1:8765", apiSwitch));
        apiStatusView = new TextView(this);
        apiStatusView.setTextSize(11);
        apiStatusView.setTextColor(MUTED);
        apiStatusView.setPadding(0, dp(4), 0, dp(6));
        api.addView(apiStatusView);
        api.addView(note("Do PC: adb forward tcp:8765 tcp:8765 → http://127.0.0.1:8765/\n"
                + "GET /state · /events?since=0 (tempo real) · /health · /apps · /logs\n"
                + "POST /tjd?cmd=… · /mtk?text=… · /sync · /alert · /mree/list · /mree/install"));
        c.addView(api);
        return scroll(c);
    }

    // ---- CONSOLE ----

    private View pageConsole() {
        LinearLayout c = pageContainer();
        LinearLayout adv = card();
        adv.addView(sectionTitle("Console de protocolo"));
        tjdCmd = field("X0,GET,1");
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
        logCard.addView(sectionTitle("Log persistente (por dia)"));
        logDay = new Spinner(this);
        logDay.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                String sel = (String) logDay.getItemAtPosition(pos);
                logLive = pos == 0;
                if (logLive) refreshLog();
                else {
                    String day = sel.length() >= 10 ? sel.substring(sel.length() - 10) : sel;
                    logView.setText(LogStore.read(day));
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> p) {
            }
        });
        logCard.addView(labeled("Dia", logDay));
        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTextColor(MUTED);
        logView.setTypeface(Typeface.MONOSPACE);
        logScroll = new ScrollView(this);
        logScroll.addView(logView);
        logCard.addView(logScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));
        logCard.addView(row(
                ghostBtn("Compartilhar", v -> startActivity(LogStore.shareIntent(currentLogDay()))),
                ghostBtn("Atualizar", v -> refreshLog())));
        logCard.addView(ghostBtn("Limpar dia", v -> {
            LogStore.clear(currentLogDay());
            liveLog.setLength(0);
            refreshLog();
        }));
        c.addView(logCard);
        return scroll(c);
    }

    private String currentLogDay() {
        if (logDay == null || logLive) return LogStore.today();
        String sel = (String) logDay.getSelectedItem();
        return sel != null && sel.length() >= 10 ? sel.substring(sel.length() - 10) : LogStore.today();
    }

    private void rebuildLogDays() {
        if (logDay == null) return;
        java.util.List<String> days = new java.util.ArrayList<>();
        days.add("Hoje · " + LogStore.today());
        for (String d : LogStore.dates()) if (!d.equals(LogStore.today())) days.add(d);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, days);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        logDay.setAdapter(a);
    }

    // ---- acoes ----

    private void doSync() {
        W26.get().syncTime();
        W26.get().battery();
        Prefs.markSync(this);
        Health.get().syncAll();
        refreshStatus();
        log("sync: hora + bateria + saúde pedidos");
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

    private void installFace() {
        FaceBuilder.Preset p = FaceBuilder.PRESETS[facePreset.getSelectedItemPosition()];
        try {
            byte[] v = FaceBuilder.build(asset(p.asset));
            String name = "lz_" + p.asset.substring(p.asset.indexOf('/') + 1);
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

    // ---- refresh ----

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
        if (notifAccessView != null)
            notifAccessView.setText(access ? "Acesso a notificações concedido."
                    : "Sem acesso: toque em “Acesso notificações” e ative o W26.");
        Health.DayStat d = Health.get().today();
        homeSteps.setText(d.steps > 0 ? String.valueOf(d.steps) : "—");
        homeHr.setText(d.hrAvg > 0 ? String.valueOf(Math.round(d.hrAvg)) : "—");
        boolean ble = W26.get().isConnected();
        boolean spp = W26.get().isSppUp();
        setChip(bleChip, "BLE: " + (ble ? "ok" : "—"), ble);
        setChip(sppChip, "SPP: " + (spp ? "ok" : "—"), spp);
        refreshApiStatus();
    }

    private void setChip(TextView chip, String text, boolean up) {
        if (chip == null) return;
        chip.setText(text);
        GradientDrawable g = new GradientDrawable();
        g.setColor(up ? 0x222DD4A7 : GHOST);
        g.setCornerRadius(dp(20));
        chip.setBackground(g);
        chip.setTextColor(up ? GREEN : MUTED);
    }

    private void refreshApiStatus() {
        if (apiStatusView == null) return;
        apiStatusView.setText(Api.get().isRunning()
                ? "API ativa em http://127.0.0.1:" + Api.PORT + "/"
                : "API desligada.");
        apiStatusView.setTextColor(Api.get().isRunning() ? GREEN : MUTED);
    }

    private void refreshHealth() {
        Health.DayStat d = Health.get().today();
        hSteps.setText(d.steps > 0 ? String.valueOf(d.steps) : "—");
        hHr.setText(d.hrAvg > 0 ? String.valueOf(Math.round(d.hrAvg)) : "—");
        hSleep.setText(d.sleepHours > 0 ? String.format(java.util.Locale.ROOT, "%.1f", d.sleepHours) : "—");
        hCal.setText(d.calorie > 0 ? String.valueOf(Math.round(d.calorie)) : "—");
        hSpo2.setText(d.spo2 > 0 ? String.valueOf(Math.round(d.spo2)) : "—");
        hTemp.setText(d.temp > 0 ? String.format(java.util.Locale.ROOT, "%.1f", d.temp) : "—");
        hBp.setText(d.bpSys > 0 ? Math.round(d.bpSys) + "/" + Math.round(d.bpDia) : "—");
        if (hDistance != null)
            hDistance.setText(d.distanceKm > 0 ? String.format(java.util.Locale.ROOT, "%.2f", d.distanceKm) : "—");
        java.util.List<Health.DayStat> week = Health.get().lastDays(7);
        float[] steps = new float[7];
        float[] hr = new float[7];
        String[] labels = new String[7];
        String[] wd = {"D", "S", "T", "Q", "Q", "S", "S"};
        java.util.Calendar cal = java.util.Calendar.getInstance();
        for (int i = 0; i < 7; i++) {
            Health.DayStat s = week.get(i);
            steps[i] = s.steps;
            hr[i] = (float) s.hrAvg;
            cal.setTimeInMillis(System.currentTimeMillis() - (6 - i) * 24L * 3600_000L);
            labels[i] = wd[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1];
        }
        stepsChart.setData(steps, labels, ACCENT, Chart.BARS, "passos");
        hrChart.setData(hr, labels, WARN, Chart.LINE, "bpm");
    }

    private void refreshDevice() {
        if (deviceInfoBox == null) return;
        deviceInfoBox.removeAllViews();
        java.util.Map<String, String> info = W26.get().info;
        deviceInfoBox.addView(infoRow("Modelo", or(info.get("model"), "Watch 6 / W26")));
        deviceInfoBox.addView(infoRow("Marca", or(info.get("brand"), "—")));
        deviceInfoBox.addView(infoRow("Firmware", or(info.get("version"), or(info.get("firmware"), "W26_B_BL6133_201128_1"))));
        deviceInfoBox.addView(infoRow("MRE", or(info.get("mre_version"), "2.0")));
        deviceInfoBox.addView(infoRow("Tela", or(info.get("lcd"), "176 × 220")));
        deviceInfoBox.addView(infoRow("Memória total", or(info.get("max_memory"), "—")));
        deviceInfoBox.addView(infoRow("Tipo/capacidades", or(info.get("devType"), "—")));
        deviceInfoBox.addView(infoRow("Protocolo TJD", or(info.get("protocol"), "—")));
        deviceInfoBox.addView(infoRow("Sensor BPM", yesno(info.get("heart_rate_sensor"))));
        deviceInfoBox.addView(infoRow("Sensor temperatura", yesno(info.get("temperature_sensor"))));
        deviceInfoBox.addView(infoRow("GPS", yesno(info.get("gps"))));
        deviceInfoBox.addView(infoRow("BLE MAC", Prefs.bleMac(this)));
        deviceInfoBox.addView(infoRow("SPP MAC", Prefs.sppMac(this)));
        int wb = W26.get().getWatchBattery();
        deviceInfoBox.addView(infoRow("Bateria", wb < 0 ? "—" : wb + "%"));
    }

    private static String or(String a, String b) {
        return a == null || a.isEmpty() ? b : a;
    }

    private static String yesno(String v) {
        if (v == null || v.isEmpty()) return "—";
        return v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes") ? "sim" : "não";
    }

    private void refreshApps() {
        if (appsBox == null) return;
        java.util.List<Apps.App> list = Apps.get().list();
        String mem = W26.get().info.get("max_memory");
        appsCountView.setText(list.size() + " apps · " + Apps.get().removable() + " removíveis"
                + (mem != null ? " · memória " + mem : ""));
        appsBox.removeAllViews();
        if (list.isEmpty()) {
            appsBox.addView(note("Toque em “Atualizar lista” com o SPP conectado para ver os apps do relógio."));
            return;
        }
        for (Apps.App a : list) appsBox.addView(appRow(a));
    }

    private View appRow(Apps.App a) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(7), 0, dp(7));
        LinearLayout.LayoutParams iip = new LinearLayout.LayoutParams(dp(26), dp(26));
        iip.rightMargin = dp(12);
        r.addView(icon(a.preinstalled ? Icon.WATCH : Icon.APPS, a.preinstalled ? MUTED : ACCENT, 26), iip);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(a.name);
        name.setTextSize(13);
        name.setTextColor(TEXT);
        name.setSingleLine(true);
        t.addView(name);
        TextView sub = new TextView(this);
        sub.setText((a.ver.isEmpty() ? "" : "v" + a.ver + " · ") + (a.preinstalled ? "fábrica (fixo)" : "instalado"));
        sub.setTextSize(10);
        sub.setTextColor(MUTED);
        t.addView(sub);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!a.preinstalled) {
            TextView del = new TextView(this);
            del.setText("Remover");
            del.setTextSize(12);
            del.setTextColor(WARN);
            del.setTypeface(Typeface.DEFAULT_BOLD);
            del.setPadding(dp(10), dp(6), dp(10), dp(6));
            del.setOnClickListener(v -> {
                W26.get().mreeUninstall(a.name);
                log("removendo " + a.name);
                ui.postDelayed(this::refreshApps, 2000);
            });
            r.addView(del);
        }
        return r;
    }

    private void refreshLog() {
        if (logView == null) return;
        rebuildLogDays();
        if (logLive) {
            String store = LogStore.read(LogStore.today());
            logView.setText(store.isEmpty() ? liveLog.toString() : store);
        }
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
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
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
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
            notifAppList.addView(appNotifRow(pm, ai, !blocked.contains(ai.packageName)));
    }

    private String label(PackageManager pm, android.content.pm.ApplicationInfo ai) {
        try {
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            return ai.packageName;
        }
    }

    private View appNotifRow(PackageManager pm, android.content.pm.ApplicationInfo ai, boolean allowed) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(7), 0, dp(7));
        android.widget.ImageView iv = new android.widget.ImageView(this);
        try {
            iv.setImageDrawable(pm.getApplicationIcon(ai));
        } catch (Exception e) {
            iv.setImageDrawable(new Icon(Icon.PLUS, MUTED, density()));
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

    // ---- callbacks ----

    @Override
    public void onBattery(int percent) {
        watchBattView.setText(percent + "%");
        if (watchRing != null) watchRing.setPercent(percent);
    }

    @Override
    public void onFindPhone(boolean start) {
        if (start) {
            log("relógio pediu: encontrar celular");
            alertPhone();
            notifyPhone("Encontrar celular", "O relógio pediu para localizar este celular.");
        } else {
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

    @Override
    public void onHealthUpdated() {
        runOnUiThread(() -> {
            refreshHealth();
            refreshStatus();
        });
    }

    @Override
    public void onMeasure(String type, double value) {
        runOnUiThread(() -> Toast.makeText(this,
                type.toUpperCase(java.util.Locale.ROOT) + ": " + value, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onAppsUpdated() {
        runOnUiThread(this::refreshApps);
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
                Weather.fetchAndPush(this);
            }
            ui.postDelayed(() -> {
                refreshDevice();
                if (W26.get().isSppUp()) Apps.get().refresh();
            }, 2500);
        }
        refreshStatus();
    }

    @Override
    public void onLog(String line) {
        log(line);
    }

    private void log(String line) {
        String stamped = java.time.LocalTime.now().withNano(0) + "  " + line + "\n";
        liveLog.append(stamped);
        if (liveLog.length() > 24000) liveLog.delete(0, liveLog.length() - 20000);
        if (logView != null && logLive && currentTab == TAB_CONSOLE) {
            logView.append(stamped);
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
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
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        LinkSvc.start(this);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null || req != REQ_VXP) return;
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
}