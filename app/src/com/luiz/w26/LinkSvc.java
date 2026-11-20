package com.luiz.w26;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** Mantem BLE+SPP vivos mesmo com o app fechado e reconecta sozinho:
 *  bluetooth ligou, relogio apareceu (ACL), app abriu, queda de link.
 *  Notificacao foreground mostra o estado; a API interna roda junto. */
public class LinkSvc extends Service {
    private static final int NOTIF_ID = 2;
    private static final String CH = "w26_link";

    private final Handler h = new Handler(Looper.getMainLooper());
    private int backoff = 5000;
    private boolean started;
    private String lastNotif = "";

    private final W26.Listener listener = new W26.Listener() {
        @Override
        public void onLog(String line) {
        }

        @Override
        public void onState(String s) {
            updateNotif();
            if ("conectado".equals(s)) {
                backoff = 5000;
                h.removeCallbacks(reconnect);
                h.postDelayed(() -> {
                    if (Prefs.wantConn(LinkSvc.this) && !W26.get().isSppUp())
                        W26.get().connectSpp(LinkSvc.this, Prefs.sppMac(LinkSvc.this));
                }, 900);
                if (Prefs.healthAuto(LinkSvc.this)) Health.get().syncAll();
            } else if ("desconectado".equals(s)) {
                scheduleReconnect();
            }
        }

        @Override
        public void onBattery(int percent) {
            updateNotif();
        }
    };

    private final Runnable reconnect = () -> {
        backoff = Math.min(backoff * 2, 60000);
        ensureConnected();
    };

    private final Runnable periodic = new Runnable() {
        @Override
        public void run() {
            if (W26.get().isConnected()) {
                W26.get().battery();
                if (Prefs.healthAuto(LinkSvc.this)) Health.get().periodic();
                if (!W26.get().isSppUp() && Prefs.wantConn(LinkSvc.this))
                    W26.get().connectSpp(LinkSvc.this, Prefs.sppMac(LinkSvc.this));
            } else {
                ensureConnected();
            }
            h.postDelayed(this, 60000);
        }
    };

    private final BroadcastReceiver radio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(a)) {
                int st = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (st == BluetoothAdapter.STATE_ON) {
                    W26.get().note("bluetooth ligou: conectando");
                    backoff = 5000;
                    ensureConnected();
                }
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(a)) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String mac = d == null ? "" : d.getAddress();
                if (mac.equalsIgnoreCase(Prefs.bleMac(c)) || mac.equalsIgnoreCase(Prefs.sppMac(c))) {
                    W26.get().note("relogio apareceu no bluetooth (" + mac + ")");
                    backoff = 5000;
                    h.postDelayed(LinkSvc.this::ensureConnected, 1200);
                }
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(a)) {
                int st = i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                if (st == BluetoothDevice.BOND_BONDED) {
                    backoff = 5000;
                    h.postDelayed(LinkSvc.this::ensureConnected, 1500);
                }
            }
        }
    };

    public static void start(Context c) {
        Intent i = new Intent(c, LinkSvc.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    /** Liga/desliga a API interna sem reiniciar o servico. */
    public static void applyApi(Context c) {
        if (Prefs.apiEnabled(c)) Api.get().start(c);
        else Api.get().stop();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LogStore.init(this);
        Health.get().init(this);
        createChannel();
        W26.get().addListener(listener);
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(radio, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(radio, f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            LogStore.append("LinkSvc: sem permissao BLUETOOTH_CONNECT; abra o app para conceder");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!started) {
            started = true;
            startForeground(NOTIF_ID, buildNotif("W26 bancada ativa"));
            h.postDelayed(periodic, 60000);
            W26.get().note("LinkSvc ativo: auto-conexao " + (Prefs.autoConnect(this) ? "ligada" : "desligada"));
        }
        applyApi(this);
        if (intent != null && intent.getBooleanExtra("connect", false)) {
            Prefs.setWantConn(this, true);
            backoff = 5000;
        }
        ensureConnected();
        return START_STICKY;
    }

    private void ensureConnected() {
        if (!Prefs.wantConn(this) || !Prefs.autoConnect(this)) return;
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null || !ad.isEnabled()) return;
        if (!W26.get().isConnected()) W26.get().connect(this, Prefs.bleMac(this));
    }

    private void scheduleReconnect() {
        if (!Prefs.wantConn(this) || !Prefs.autoConnect(this)) return;
        h.removeCallbacks(reconnect);
        h.postDelayed(reconnect, backoff);
    }

    private void updateNotif() {
        String s;
        if (W26.get().isConnected()) {
            int b = W26.get().getWatchBattery();
            s = "conectado" + (W26.get().isSppUp() ? " · BLE+SPP" : " · BLE")
                    + (b >= 0 ? " · " + b + "%" : "");
        } else if ("conectando".equals(W26.get().getState()) || "lendo servicos".equals(W26.get().getState())) {
            s = "conectando…";
        } else {
            s = Prefs.wantConn(this) ? "reconectando…" : "desconectado";
        }
        if (s.equals(lastNotif)) return;
        lastNotif = s;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotif(s));
    }

    private Notification buildNotif(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("W26 Bancada")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CH, "Conexão W26", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        W26.get().removeListener(listener);
        h.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(radio);
        } catch (Exception ignored) {
        }
        Api.get().stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Reinicia o servico depois de boot/update (so se o usuario quer conexao). */
    public static class Boot extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            if (!Prefs.wantConn(c) || !Prefs.autoConnect(c)) return;
            if (Build.VERSION.SDK_INT >= 31
                    && c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                return;
            start(c);
        }
    }
}