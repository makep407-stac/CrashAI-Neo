package sf.grh.crash;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
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

    private static final int BG = Color.rgb(10, 16, 30);
    private static final int PANEL = Color.rgb(22, 32, 51);
    private static final int BLUE = Color.rgb(45, 158, 255);
    private static final int MUTED = Color.rgb(157, 180, 208);

    private boolean waitingForOverlayPermission;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        showLogin();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (waitingForOverlayPermission) {
            waitingForOverlayPermission = false;

            if (Build.VERSION.SDK_INT < 23
                    || Settings.canDrawOverlays(this)) {
                startBubbleService();
            } else {
                Toast.makeText(
                    this,
                    "لم يتم منح إذن النافذة العائمة",
                    Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    private GradientDrawable shape(
            int color,
            int radiusDp,
            int strokeColor,
            int strokeDp) {

        float density =
                getResources().getDisplayMetrics().density;

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusDp * density);

        if (strokeDp > 0) {
            drawable.setStroke(
                Math.max(1, (int) (strokeDp * density)),
                strokeColor
            );
        }

        return drawable;
    }

    private TextView label(
            String value,
            int size,
            int color,
            boolean bold) {

        TextView view = new TextView(this);

        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);

        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        return view;
    }

    private LinearLayout page() {
        LinearLayout layout = new LinearLayout(this);

        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        layout.setPadding(
            dp(24),
            dp(20),
            dp(24),
            dp(24)
        );

        layout.setBackgroundColor(BG);

        return layout;
    }

    private int dp(int value) {
        return (int) (
            value * getResources().getDisplayMetrics().density
            + 0.5f
        );
    }

    private LinearLayout.LayoutParams params(
            int width,
            int height) {

        return new LinearLayout.LayoutParams(
            width < 0 ? width : dp(width),
            height < 0 ? height : dp(height)
        );
    }

    private EditText input(String hint) {
        EditText edit = new EditText(this);

        edit.setSingleLine(true);
        edit.setTextColor(Color.WHITE);
        edit.setHintTextColor(Color.rgb(125, 145, 171));
        edit.setHint(hint);
        edit.setTextSize(15);

        edit.setPadding(
            dp(16),
            dp(4),
            dp(16),
            dp(4)
        );

        edit.setBackground(
            shape(PANEL, 16, Color.rgb(52, 77, 110), 1)
        );

        return edit;
    }

    private Button button(String title) {
        Button button = new Button(this);

        button.setText(title);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setBackground(
            shape(BLUE, 26, BLUE, 1)
        );

        return button;
    }

    private void showLogin() {
        LinearLayout root = page();
        root.setGravity(Gravity.CENTER);

        root.addView(
            label("✈     ✦     ✈     ✦     ✈",
                23, BLUE, true),
            params(-1, 48)
        );

        root.addView(
            label("CRASHAI NEO",
                29, Color.WHITE, true),
            params(-1, 48)
        );

        root.addView(
            label("رفيقك الذكي للعبة Crash",
                15, MUTED, false),
            params(-1, 34)
        );

        root.addView(
            label("مرحباً بك",
                21, Color.WHITE, true),
            params(-1, 56)
        );

        root.addView(
            label("سجّل الدخول للوصول إلى واجهة التطبيق",
                13, MUTED, false),
            params(-1, 34)
        );

        EditText account =
                input("معرّف حسابك على 1xBet");

        LinearLayout.LayoutParams accountParams =
                params(-1, 54);

        accountParams.topMargin = dp(16);
        root.addView(account, accountParams);

        EditText pin = input("رمز التطبيق");
        pin.setInputType(129);

        LinearLayout.LayoutParams pinParams =
                params(-1, 54);

        pinParams.topMargin = dp(12);
        root.addView(pin, pinParams);

        root.addView(
            label(
                "التحقق من حساب 1xBet غير متاح دون واجهة رسمية.",
                12,
                MUTED,
                false
            ),
            params(-1, 48)
        );

        Button login = button("دخول  →");

        LinearLayout.LayoutParams loginParams =
                params(-1, 54);

        loginParams.topMargin = dp(12);
        root.addView(login, loginParams);

        login.setOnClickListener(v -> {

            if (account.getText().toString().trim().isEmpty()) {
                account.setError("أدخل معرّف الحساب");
                return;
            }

            if (!"sf.1999".equals(
                    pin.getText().toString())) {

                pin.setError("رمز التطبيق غير صحيح");
                return;
            }

            new AlertDialog.Builder(this)
                .setTitle("تنبيه")
                .setMessage(
                    "تم قبول الرمز محلياً فقط. التطبيق لا يتحقق "
                    + "من حساب 1xBet ولا يعرض تنبؤات حقيقية "
                    + "حتى ربط محرك بيانات."
                )
                .setPositiveButton(
                    "متابعة",
                    (dialog, which) -> showDashboard()
                )
                .setNegativeButton("إلغاء", null)
                .show();
        });

        root.addView(
            label(
                "CRASHAI NEO  •  النسخة التجريبية",
                11,
                Color.rgb(95, 119, 150),
                false
            ),
            params(-1, 42)
        );

        setContentView(root);
    }

    private void showDashboard() {
        LinearLayout root = page();

        root.addView(
            label("✈  CRASHAI NEO",
                21, Color.WHITE, true),
            params(-1, 48)
        );

        TextView status = label(
            "● غير متصل بمصدر بيانات",
            12,
            Color.rgb(255, 194, 89),
            false
        );

        root.addView(status, params(-1, 32));

        TextView orb = label(
            "—",
            42,
            Color.WHITE,
            true
        );

        orb.setBackground(
            shape(Color.rgb(14, 48, 83), 140, BLUE, 3)
        );

        orb.setElevation(dp(8));

        LinearLayout.LayoutParams orbParams =
                params(220, 220);

        orbParams.gravity = Gravity.CENTER_HORIZONTAL;
        orbParams.topMargin = dp(32);
        orbParams.bottomMargin = dp(18);

        root.addView(orb, orbParams);

        root.addView(
            label("التنبؤ القادم",
                18, Color.WHITE, true),
            params(-1, 36)
        );

        root.addView(
            label("لا توجد نتيجة حقيقية حالياً",
                13, MUTED, false),
            params(-1, 34)
        );

        root.addView(
            label(
                "ستظهر النتائج هنا بعد ربط محرك التنبؤ "
                + "الأصلي ومصدر بيانات موثوق.",
                12,
                MUTED,
                false
            ),
            params(-1, 58)
        );

        Button bubble = button("إعداد الفقاعة العائمة");

        LinearLayout.LayoutParams bubbleParams =
                params(-1, 52);

        bubbleParams.topMargin = dp(18);
        root.addView(bubble, bubbleParams);

        bubble.setOnClickListener(
            v -> requestBubblePermission()
        );

        Button info = button("معلومات التطبيق");

        LinearLayout.LayoutParams infoParams =
                params(-1, 48);

        infoParams.topMargin = dp(10);

        info.setBackground(
            shape(PANEL, 24, Color.rgb(52, 77, 110), 1)
        );

        root.addView(info, infoParams);

        info.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("حول CrashAI Neo")
                .setMessage(
                    "واجهة تجريبية. لا تتصل هذه النسخة "
                    + "بخوادم 1xBet ولا تتنبأ بنتائج اللعبة."
                )
                .setPositiveButton("حسناً", null)
                .show()
        );

        setContentView(root);
    }

    private void requestBubblePermission() {

        if (Build.VERSION.SDK_INT >= 23
                && !Settings.canDrawOverlays(this)) {

            waitingForOverlayPermission = true;

            try {
                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
                );

                startActivity(intent);

            } catch (Exception exception) {

                waitingForOverlayPermission = false;

                Toast.makeText(
                    this,
                    "تعذر فتح إعدادات الإذن",
                    Toast.LENGTH_LONG
                ).show();
            }

            return;
        }

        startBubbleService();
    }

    private void startBubbleService() {
        try {
            startService(
                new Intent(this, BubbleService.class)
            );

            Toast.makeText(
                this,
                "تم تشغيل الفقاعة التجريبية",
                Toast.LENGTH_SHORT
            ).show();

        } catch (Exception exception) {

            Toast.makeText(
                this,
                "تعذر تشغيل الفقاعة",
                Toast.LENGTH_LONG
            ).show();
        }
    }
}
