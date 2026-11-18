package com.luiz.w26;

import android.app.Notification;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Encaminha notificacoes do celular para o relogio via MTK 3.0. */
public class NotiSvc extends NotificationListenerService {
    private String lastKey = "";
    private long lastTime;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getPackageName().equals(getPackageName())) return;
        if (!Prefs.notifEnabled(this)) return;
        String pkg = sbn.getPackageName();
        if (Prefs.notifBlocked(this).contains(pkg)) return;
        Notification n = sbn.getNotification();
        if (n == null) return;
        if (Prefs.notifSkipSystem(this) && isSystemNoise(pkg, n)) return;
        Bundle ex = n.extras;
        CharSequence t = ex.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence x = ex.getCharSequence(Notification.EXTRA_TEXT);
        if (x == null) x = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (t == null && x == null) return;

        long now = System.currentTimeMillis();
        if (sbn.getKey().equals(lastKey) && now - lastTime < 1500) return;
        lastKey = sbn.getKey();
        lastTime = now;

        String app;
        try {
            PackageManager pm = getPackageManager();
            app = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            app = pkg;
        }
        W26.get().notifyWatch(app,
                t == null ? app : t.toString(),
                x == null ? "" : x.toString());
    }

    /** Carga, midia (play/pause/stop), progresso e avisos do sistema nao devem vibrar o relogio. */
    private static boolean isSystemNoise(String pkg, Notification n) {
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return true;
        if (n.extras != null && n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return true;
        String cat = n.category;
        if (Notification.CATEGORY_SERVICE.equals(cat) || Notification.CATEGORY_TRANSPORT.equals(cat)
                || Notification.CATEGORY_PROGRESS.equals(cat) || Notification.CATEGORY_SYSTEM.equals(cat)
                || Notification.CATEGORY_STATUS.equals(cat)) return true;
        return "android".equals(pkg) || "com.android.systemui".equals(pkg);
    }
}
