package com.yoav.whatsapptimecounter;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48,48,48,48);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("WhatsApp Time Counter");
        title.setTextSize(26); title.setTextColor(Color.rgb(7,94,84)); title.setGravity(Gravity.CENTER);

        TextView info = new TextView(this);
        info.setText("\nמונה קטן יופיע אוטומטית כש-WhatsApp פתוח.\n\nהוא מציג את זמן השימוש הנוכחי ואת מספר הפתיחות ב-24 השעות האחרונות.\n\nכדי להפעיל: לחץ למטה, מצא WhatsApp Time Counter והפעל את השירות.");
        info.setTextSize(17); info.setTextColor(Color.DKGRAY); info.setGravity(Gravity.CENTER);

        Button button = new Button(this);
        button.setText("הפעל את המונה");
        button.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(info, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }
}
