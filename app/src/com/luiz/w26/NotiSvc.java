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
        Notification n = sbn.getNotification();
        if (n == null || (n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return;
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
            app = pm.getApplicationLabel(pm.getApplicationInfo(sbn.getPackageName(), 0)).toString();
        } catch (Exception e) {
            app = sbn.getPackageName();
        }
        W26.get().notifyWatch(app,
                t == null ? app : t.toString(),
                x == null ? "" : x.toString());
    }
}
