package sf.grh.crash;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private final int bg = Color.rgb(12, 17, 31);
    private final int blue = Color.rgb(47, 160, 255);
    private EditText accountInput, pinInput;
    private LinearLayout root;
    private boolean waitingForOverlayPermission = false;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        showLogin();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForOverlayPermission) {
            waitingForOverlayPermission = false;
            if (Settings.canDrawOverlays(this)) {
                startService(new Intent(this, BubbleService.class));
                Toast.makeText(this, "تم تشغيل الفقاعة العائمة",
                        Toast.LENGTH_SHORT).show();
            } else {
                showPredictionFallback(
                    "النافذة العائمة غير متاحة؛ يعرض التطبيق المحتوى داخل نافذته.");
            }
        }
    }

    private GradientDrawable shape(int color, int radius,
                                   int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(26, 20, 26, 24);
        l.setBackgroundColor(bg);
        return l;
    }

    private void showLogin() {
        root = column();
        root.setGravity(Gravity.CENTER);

        root.addView(text("✈     ✦       ✈       ·       ✦      ✈",
                24, blue, true), new LinearLayout.LayoutParams(-1, 58));

        TextView title = text("CRASHAI NEO", 28, Color.WHITE, true);
        title.setLetterSpacing(0.12f);
        root.addView(title, new LinearLayout.LayoutParams(-1, 48));

        root.addView(text("رفيقك الذكي للعبة Crash", 15,
                Color.rgb(165, 185, 210), false),
                new LinearLayout.LayoutParams(-1, 34));

        TextView cardTitle = text("تسجيل الدخول", 20, Color.WHITE, true);
        LinearLayout.LayoutParams ctp =
                new LinearLayout.LayoutParams(-1, 50);
        ctp.topMargin = 22;
        root.addView(cardTitle, ctp);

        accountInput = input("أدخل ID حساب 1xBet");
        root.addView(accountInput, fieldParams());

        pinInput = input("رمز الدخول إلى التطبيق");
        pinInput.setInputType(129);
        LinearLayout.LayoutParams p = fieldParams();
        p.topMargin = 12;
        root.addView(pinInput, p);

        root.addView(text(
                "التحقق من حساب 1xBet يحتاج واجهة رسمية.",
                12, Color.rgb(155, 170, 192), false),
                new LinearLayout.LayoutParams(-1, 48));

        Button go = new Button(this);
        go.setText("دخول");
        go.setTextColor(Color.WHITE);
        go.setTextSize(16);
        go.setAllCaps(false);
        go.setBackground(shape(blue, 32, blue, 1));

        LinearLayout.LayoutParams gp =
                new LinearLayout.LayoutParams(-1, 54);
        gp.topMargin = 14;
        root.addView(go, gp);

        go.setOnClickListener(v -> {
            String id = accountInput.getText().toString().trim();
            String pin = pinInput.getText().toString();

            if (id.isEmpty()) {
                accountInput.setError("أدخل ID الحساب");
                return;
            }
            if (!"sf.1999".equals(pin)) {
                pinInput.setError("رمز التطبيق غير صحيح");
                return;
            }

            new AlertDialog.Builder(this)
                .setTitle("ملاحظة")
                .setMessage("تم قبول رمز التطبيق محليًا. هذه واجهة تجريبية ولا تتحقق من حساب 1xBet.")
                .setPositiveButton("متابعة",
                    (d, w) -> showPrediction())
                .setNegativeButton("إلغاء", null)
                .show();
        });

        root.addView(text("SF.GR H.CRASH • واجهة أولية",
                11, Color.rgb(95, 119, 150), false),
                new LinearLayout.LayoutParams(-1, 42));

        setContentView(root);
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.rgb(130, 147, 170));
        e.setHint(hint);
        e.setTextSize(15);
        e.setPadding(18, 4, 18, 4);
        e.setBackground(shape(
                Color.rgb(24, 32, 49), 18,
                Color.rgb(54, 76, 108), 1));
        return e;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, 56);
        p.topMargin = 10;
        return p;
    }

    private void showPrediction() {
        root = column();
        root.setGravity(Gravity.CENTER);

        root.addView(text("CRASHAI NEO", 18,
                Color.rgb(170, 199, 228), true),
                new LinearLayout.LayoutParams(-1, 44));

        TextView orb = text("—", 36, Color.WHITE, true);
        orb.setBackground(shape(
                Color.rgb(15, 49, 86), 180, blue, 4));
        orb.setElevation(18);

        LinearLayout.LayoutParams op =
                new LinearLayout.LayoutParams(220, 220);
        op.topMargin = 36;
        op.bottomMargin = 22;
        root.addView(orb, op);

        root.addView(text("بانتظار مصدر بيانات الجولة", 14,
                Color.rgb(158, 190, 220), false),
                new LinearLayout.LayoutParams(-1, 42));

        root.addView(text("هذه واجهة شكلية؛ لا تعرض توقعًا حقيقيًا بعد.",
                12, Color.rgb(120, 145, 175), false),
                new LinearLayout.LayoutParams(-1, 40));

        Button bubble = new Button(this);
        bubble.setText("إعداد الفقاعة العائمة");
        bubble.setAllCaps(false);
        bubble.setTextColor(Color.WHITE);
        bubble.setBackground(shape(blue, 28, blue, 1));
        root.addView(bubble,
                new LinearLayout.LayoutParams(-1, 52));

        bubble.setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= 23
                    && !Settings.canDrawOverlays(this)) {
                waitingForOverlayPermission = true;
                try {
                    Intent i = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception ex) {
                    waitingForOverlayPermission = false;
                    showPredictionFallback(
                        "تعذر فتح الإعدادات؛ سيعمل التطبيق داخل نافذته.");
                }
            } else {
                try {
                    startService(new Intent(this, BubbleService.class));
                    Toast.makeText(this, "تم تشغيل الفقاعة التجريبية",
                            Toast.LENGTH_SHORT).show();
                } catch (Exception ex) {
                    showPredictionFallback(
                        "تعذر تشغيل النافذة العائمة.");
                }
            }
        });

        setContentView(root);
    }

    private void showPredictionFallback(String message) {
        root = column();
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);

        root.addView(text("CRASHAI NEO", 18,
                Color.rgb(170, 199, 228), true),
                new LinearLayout.LayoutParams(-1, 44));

        TextView orb = text("—", 36, Color.WHITE, true);
        orb.setBackground(shape(
                Color.rgb(15, 49, 86), 180, blue, 4));
        orb.setElevation(18);

        LinearLayout.LayoutParams op =
                new LinearLayout.LayoutParams(220, 220);
        op.topMargin = 36;
        op.bottomMargin = 22;
        root.addView(orb, op);

        root.addView(text(message, 14,
                Color.rgb(158, 190, 220), false),
                new LinearLayout.LayoutParams(-1, 70));

        root.addView(text(
                "واجهة بديلة؛ لا تعرض توقعًا حقيقيًا حتى ربط مصدر بيانات مصرح به.",
                12, Color.rgb(120, 145, 175), false),
                new LinearLayout.LayoutParams(-1, 58));

        Button retry = new Button(this);
        retry.setText("إعادة محاولة النافذة العائمة");
        retry.setAllCaps(false);
        retry.setTextColor(Color.WHITE);
        retry.setBackground(shape(blue, 28, blue, 1));
        root.addView(retry,
                new LinearLayout.LayoutParams(-1, 52));

        retry.setOnClickListener(v -> showPrediction());
        setContentView(root);
    }
}
