package com.yoav.whatsapptimecounter;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WhatsAppAccessibilityService extends AccessibilityService {
    private static final String WA = "com.whatsapp";
    private static final String WAB = "com.whatsapp.w4b";
    private static final String PREFS = "usage";
    private static final long RECONCILE_DELAY_MS = 180;
    private static final long VERIFY_DELAY_MS = 320;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private PowerManager powerManager;
    private SharedPreferences prefs;
    private WindowManager.LayoutParams bubbleParams;
    private TextView bubble;
    private long sessionStart;
    private long savedTodayMs;
    private String activeDay;
    private int todayOpens;
    private boolean inWhatsApp;
    private boolean screenReceiverRegistered;
    private float touchStartX, touchStartY;
    private int windowStartX, windowStartY;

    private final Runnable reconcileForeground = this::reconcileForegroundNow;
    private final Runnable pendingEnter = () -> {
        if (!inWhatsApp && isInteractive() && isWhatsApp(foregroundPackage())) {
            beginSession();
        }
    };
    private final Runnable pendingExit = () -> {
        String pkg = foregroundPackage();
        if (inWhatsApp && (!isInteractive()
                || (pkg != null && !isWhatsApp(pkg) && !isTransientPackage(pkg)))) {
            finishSession();
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                cancelForegroundChecks();
                finishSession();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                scheduleReconcile();
            }
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!inWhatsApp || bubble == null) return;
            if (!isInteractive()) {
                finishSession();
                return;
            }
            rolloverIfNeeded();
            updateBubble();
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onServiceConnected() {
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        powerManager = (PowerManager)getSystemService(POWER_SERVICE);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadToday();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, filter);
        screenReceiverRegistered = true;

        scheduleReconcile();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        scheduleReconcile();
    }

    private void scheduleReconcile() {
        handler.removeCallbacks(reconcileForeground);
        handler.postDelayed(reconcileForeground, RECONCILE_DELAY_MS);
    }

    private void reconcileForegroundNow() {
        if (!isInteractive()) {
            handler.removeCallbacks(pendingEnter);
            if (inWhatsApp) finishSession();
            return;
        }

        String pkg = foregroundPackage();
        if (pkg == null) return;

        if (isWhatsApp(pkg)) {
            handler.removeCallbacks(pendingExit);
            if (!inWhatsApp) {
                handler.removeCallbacks(pendingEnter);
                handler.postDelayed(pendingEnter, VERIFY_DELAY_MS);
            }
        } else if (!isTransientPackage(pkg)) {
            handler.removeCallbacks(pendingEnter);
            if (inWhatsApp) {
                handler.removeCallbacks(pendingExit);
                handler.postDelayed(pendingExit, VERIFY_DELAY_MS);
            }
        }
    }

    private boolean isInteractive() {
        return powerManager != null && powerManager.isInteractive();
    }

    private String foregroundPackage() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            CharSequence pkg = root.getPackageName();
            if (pkg != null) return pkg.toString();
        }

        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    if (!window.isActive() && !window.isFocused()) continue;
                    AccessibilityNodeInfo windowRoot = window.getRoot();
                    if (windowRoot == null) continue;
                    CharSequence pkg = windowRoot.getPackageName();
                    if (pkg != null) return pkg.toString();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isWhatsApp(String pkg) {
        return WA.equals(pkg) || WAB.equals(pkg);
    }

    private boolean isTransientPackage(String pkg) {
        return getPackageName().equals(pkg)
                || "com.android.systemui".equals(pkg)
                || "com.google.android.inputmethod.latin".equals(pkg)
                || "com.samsung.android.honeyboard".equals(pkg);
    }

    private void beginSession() {
        if (inWhatsApp || !isInteractive()) return;
        loadToday();
        inWhatsApp = true;
        sessionStart = System.currentTimeMillis();
        todayOpens++;
        saveToday();
        showBubble();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void finishSession() {
        if (!inWhatsApp) return;
        rolloverIfNeeded();
        savedTodayMs += Math.max(0, System.currentTimeMillis() - sessionStart);
        inWhatsApp = false;
        saveToday();
        handler.removeCallbacks(ticker);
        hideBubble();
    }

    private String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private void loadToday() {
        String today = todayKey();
        if (!today.equals(prefs.getString("daily_day", ""))) {
            activeDay = today;
            savedTodayMs = 0;
            todayOpens = 0;
            saveToday();
        } else {
            activeDay = today;
            savedTodayMs = prefs.getLong("daily_ms", 0);
            todayOpens = prefs.getInt("daily_opens", migrateLegacyOpenCount());
        }
    }

    private int migrateLegacyOpenCount() {
        String raw = prefs.getString("opens", "");
        if (raw.isEmpty()) return 0;

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        long cutoff = cal.getTimeInMillis();

        int count = 0;
        for (String value : raw.split(",")) {
            try {
                if (Long.parseLong(value) >= cutoff) count++;
            } catch (NumberFormatException ignored) {}
        }
        prefs.edit().remove("opens").apply();
        return count;
    }

    private void rolloverIfNeeded() {
        String today = todayKey();
        if (today.equals(activeDay)) return;

        activeDay = today;
        savedTodayMs = 0;
        todayOpens = inWhatsApp ? 1 : 0;
        sessionStart = System.currentTimeMillis();
        saveToday();
    }

    private void saveToday() {
        prefs.edit()
                .putString("daily_day", activeDay)
                .putLong("daily_ms", savedTodayMs)
                .putInt("daily_opens", todayOpens)
                .apply();
    }

    private void updateBubble() {
        long totalMs = savedTodayMs + Math.max(0, System.currentTimeMillis() - sessionStart);
        long totalSec = totalMs / 1000;
        bubble.setText(String.format(Locale.getDefault(),
                "  %02d:%02d:%02d  |  %d  ",
                totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60, todayOpens));
    }

    private void showBubble() {
        if (bubble != null) return;

        bubble = new TextView(this);
        bubble.setTextSize(14);
        bubble.setTextColor(Color.WHITE);
        bubble.setGravity(Gravity.CENTER);
        bubble.setPadding(18, 10, 18, 10);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(225, 7, 94, 84));
        bg.setCornerRadius(40);
        bubble.setBackground(bg);

        bubbleParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = prefs.getInt("bubble_x", 80);
        bubbleParams.y = prefs.getInt("bubble_y", 90);

        bubble.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartX = e.getRawX();
                    touchStartY = e.getRawY();
                    windowStartX = bubbleParams.x;
                    windowStartY = bubbleParams.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    bubbleParams.x = windowStartX + Math.round(e.getRawX() - touchStartX);
                    bubbleParams.y = windowStartY + Math.round(e.getRawY() - touchStartY);
                    try { wm.updateViewLayout(bubble, bubbleParams); } catch (Exception ignored) {}
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    prefs.edit()
                            .putInt("bubble_x", bubbleParams.x)
                            .putInt("bubble_y", bubbleParams.y)
                            .apply();
                    return true;
                default:
                    return false;
            }
        });

        wm.addView(bubble, bubbleParams);
    }

    private void hideBubble() {
        if (bubble == null) return;
        try { wm.removeView(bubble); } catch (Exception ignored) {}
        bubble = null;
        bubbleParams = null;
    }

    private void cancelForegroundChecks() {
        handler.removeCallbacks(reconcileForeground);
        handler.removeCallbacks(pendingEnter);
        handler.removeCallbacks(pendingExit);
    }

    @Override public void onInterrupt() {
        cancelForegroundChecks();
        finishSession();
    }

    @Override public void onDestroy() {
        cancelForegroundChecks();
        handler.removeCallbacks(ticker);
        finishSession();
        hideBubble();
        if (screenReceiverRegistered) {
            try { unregisterReceiver(screenReceiver); } catch (IllegalArgumentException ignored) {}
            screenReceiverRegistered = false;
        }
        super.onDestroy();
    }
}
