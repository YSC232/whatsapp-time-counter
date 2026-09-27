package com.yoav.whatsapptimecounter;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class WhatsAppAccessibilityService extends AccessibilityService {
    private static final String WA = "com.whatsapp";
    private static final String WAB = "com.whatsapp.w4b";
    private WindowManager wm;
    private TextView bubble;
    private long sessionStart;
    private boolean inWhatsApp = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (inWhatsApp && bubble != null) {
                long sec = Math.max(0, (System.currentTimeMillis() - sessionStart) / 1000);
                long min = sec / 60; sec %= 60;
                bubble.setText(String.format("  %02d:%02d  •  24h: %d פתיחות  ", min, sec, count24h()));
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override protected void onServiceConnected() {
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence p = event.getPackageName();
        if (p == null) return;
        String pkg = p.toString();
        boolean nowWA = WA.equals(pkg) || WAB.equals(pkg);

        if (nowWA && !inWhatsApp) {
            inWhatsApp = true;
            sessionStart = System.currentTimeMillis();
            recordOpen(sessionStart);
            showBubble();
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        } else if (!nowWA && inWhatsApp && !getPackageName().equals(pkg) && !"com.android.systemui".equals(pkg)) {
            inWhatsApp = false;
            handler.removeCallbacks(ticker);
            hideBubble();
        }
    }

    private void showBubble() {
        if (bubble != null) return;
        bubble = new TextView(this);
        bubble.setTextSize(14);
        bubble.setTextColor(Color.WHITE);
        bubble.setGravity(Gravity.CENTER);
        bubble.setPadding(14,8,14,8);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(225, 7, 94, 84));
        bg.setCornerRadius(40);
        bubble.setBackground(bg);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = 90;
        wm.addView(bubble, lp);
    }

    private void hideBubble() {
        if (bubble != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) {}
            bubble = null;
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
                try { long t = Long.parseLong(s); if (t >= cutoff) out.add(t); } catch(Exception ignored) {}
            }
        }
        return out;
    }

    private void saveTimes(List<Long> times) {
        StringBuilder b = new StringBuilder();
        for (Long t : times) { if (b.length() > 0) b.append(','); b.append(t); }
        getSharedPreferences("usage", MODE_PRIVATE).edit().putString("opens", b.toString()).apply();
    }

    @Override public void onInterrupt() { hideBubble(); }
    @Override public void onDestroy() { handler.removeCallbacks(ticker); hideBubble(); super.onDestroy(); }
}
