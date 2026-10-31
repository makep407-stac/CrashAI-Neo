package com.aimassist.lab;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 32, 24, 24);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(13, 16, 22));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = new TextView(this);
        title.setText("AIM ASSIST LAB"); title.setTextSize(26);
        title.setTextColor(Color.rgb(255, 139, 43)); title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, 90));

        TextView details = new TextView(this);
        details.setText("مساعد بصري تجريبي للألعاب\n\nهذا النموذج يعرض مؤشرًا عائمًا قابلًا للتحريك فوق اللعبة. لا يكتشف الرؤوس تلقائيًا ولا يغيّر التصويب أو مسار الطلقات.");
        details.setTextSize(17); details.setTextColor(Color.WHITE); details.setPadding(18, 24, 18, 24);
        details.setBackgroundColor(Color.rgb(29, 34, 43));
        root.addView(details, new LinearLayout.LayoutParams(-1, -2));

        Button start = new Button(this); start.setText("تشغيل المؤشر العائم");
        root.addView(start, new LinearLayout.LayoutParams(-1, 64));
        start.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            } else { startService(new Intent(this, OverlayService.class)); }
        });

        Button stop = new Button(this); stop.setText("إيقاف المؤشر");
        root.addView(stop, new LinearLayout.LayoutParams(-1, 64));
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));

        TextView foot = new TextView(this); foot.setText("نسخة تجريبية 0.1 • Android 12"); foot.setTextColor(Color.LTGRAY); foot.setGravity(Gravity.CENTER);
        root.addView(foot, new LinearLayout.LayoutParams(-1, 60));
        setContentView(root);
    }
}
