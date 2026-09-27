package com.yoav.whatsapptimecounter;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WhatsAppAccessibilityService extends AccessibilityService {
    private static final String WA = "com.whatsapp";
    private static final String WAB = "com.whatsapp.w4b";
    private WindowManager wm;
    private WindowManager.LayoutParams bubbleParams;
    private TextView bubble;
    private long sessionStart;
    private long savedTodayMs;
    private String activeDay;
    private boolean inWhatsApp = false;
    private float touchStartX, touchStartY;
    private int windowStartX, windowStartY;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (inWhatsApp && bubble != null) {
                rolloverIfNeeded();
                long totalMs = savedTodayMs + Math.max(0, System.currentTimeMillis() - sessionStart);
                long totalSec = totalMs / 1000;
                long hours = totalSec / 3600;
                long minutes = (totalSec % 3600) / 60;
                long seconds = totalSec % 60;
                bubble.setText(String.format(Locale.getDefault(),
                    "  %02d:%02d:%02d  •  24h: %d פתיחות  ",
                    hours, minutes, seconds, count24h()));
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override protected void onServiceConnected() {
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        loadToday();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence p = event.getPackageName();
        if (p == null) return;
        String pkg = p.toString();
        boolean nowWA = WA.equals(pkg) || WAB.equals(pkg);

        if (nowWA && !inWhatsApp) {
            loadToday();
            inWhatsApp = true;
            sessionStart = System.currentTimeMillis();
            recordOpen(sessionStart);
            showBubble();
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        } else if (!nowWA && inWhatsApp && !getPackageName().equals(pkg)
                && !"com.android.systemui".equals(pkg)
                && !"com.google.android.inputmethod.latin".equals(pkg)
                && !"com.samsung.android.honeyboard".equals(pkg)) {
            finishSession();
        }
    }

    private String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private void loadToday() {
        SharedPreferences p = getSharedPreferences("usage", MODE_PRIVATE);
        String today = todayKey();
        String storedDay = p.getString("daily_day", "");
        if (!today.equals(storedDay)) {
            activeDay = today;
            savedTodayMs = 0;
            p.edit().putString("daily_day", today).putLong("daily_ms", 0).apply();
        } else {
            activeDay = today;
            savedTodayMs = p.getLong("daily_ms", 0);
        }
    }

    private void rolloverIfNeeded() {
        String today = todayKey();
        if (!today.equals(activeDay)) {
            activeDay = today;
            savedTodayMs = 0;
            sessionStart = System.currentTimeMillis();
            getSharedPreferences("usage", MODE_PRIVATE).edit()
                .putString("daily_day", today).putLong("daily_ms", 0).apply();
        }
    }

    private void finishSession() {
        if (!inWhatsApp) return;
        rolloverIfNeeded();
        savedTodayMs += Math.max(0, System.currentTimeMillis() - sessionStart);
        getSharedPreferences("usage", MODE_PRIVATE).edit()
            .putString("daily_day", activeDay).putLong("daily_ms", savedTodayMs).apply();
        inWhatsApp = false;
        handler.removeCallbacks(ticker);
        hideBubble();
    }

    private void showBubble() {
        if (bubble != null) return;
        bubble = new TextView(this);
        bubble.setTextSize(14);
        bubble.setTextColor(Color.WHITE);
        bubble.setGravity(Gravity.CENTER);
        bubble.setPadding(18,10,18,10);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(225, 7, 94, 84));
        bg.setCornerRadius(40);
        bubble.setBackground(bg);

        SharedPreferences p = getSharedPreferences("usage", MODE_PRIVATE);
        bubbleParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = p.getInt("bubble_x", 80);
        bubbleParams.y = p.getInt("bubble_y", 90);

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
                    getSharedPreferences("usage", MODE_PRIVATE).edit()
                        .putInt("bubble_x", bubbleParams.x)
                        .putInt("bubble_y", bubbleParams.y).apply();
                    return true;
            }
            return false;
        });

        wm.addView(bubble, bubbleParams);
    }

    private void hideBubble() {
        if (bubble != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) {}
            bubble = null;
            bubbleParams = null;
        }
    }

    private void recordOpen(long t) {
        List<Long> times = loadTimes();
        times.add(t);
        saveTimes(times);
    }

    private int count24h() {
        List<Long> times = loadTimes();
        saveTimes(times);
        return times.size();
    }

    private List<Long> loadTimes() {
        long cutoff = System.currentTimeMillis() - 24L*60*60*1000;
        String raw = getSharedPreferences("usage", MODE_PRIVATE).getString("opens", "");
        List<Long> out = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (String s : raw.split(",")) {
                try {
                    long t = Long.parseLong(s);
                    if (t >= cutoff) out.add(t);
                } catch(Exception ignored) {}
            }
        }
        return out;
    }

    private void saveTimes(List<Long> times) {
        StringBuilder b = new StringBuilder();
        for (Long t : times) {
            if (b.length() > 0) b.append(',');
            b.append(t);
        }
        getSharedPreferences("usage", MODE_PRIVATE).edit().putString("opens", b.toString()).apply();
    }

    @Override public void onInterrupt() { finishSession(); }
    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);
        finishSession();
        hideBubble();
        super.onDestroy();
    }
}
