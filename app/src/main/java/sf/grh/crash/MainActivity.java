
package sf.grh.crash;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private static final int BG = Color.rgb(3, 5, 10);
    private static final int BLUE = Color.rgb(30, 145, 255);
    private static final int MUTED = Color.rgb(120, 145, 175);

    private static final String API_URL =
            "https://crashai-neo-api.onrender.com/health";

    private TextView statusView;

    private int dp(float value) {
        return (int) (value *
                getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }

        showLogin();
    }

    private GradientDrawable makeShape(
            int fill, int stroke, int strokeWidth) {

        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(18));

        if (strokeWidth > 0) {
            drawable.setStroke(dp(strokeWidth), stroke);
        }

        return drawable;
    }

    // دائرة فعلية وليست مربعاً مستدير الحواف
    private GradientDrawable makeCircle(
            int fill, int stroke, int strokeWidth) {

        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);

        if (strokeWidth > 0) {
            drawable.setStroke(dp(strokeWidth), stroke);
        }

        return drawable;
    }

    private TextView makeLabel(
            String text, int size, int color, boolean bold) {

        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);

        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        return view;
    }

    private EditText makeInput(String hint) {
        EditText input = new EditText(this);

        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(MUTED);
        input.setHint(hint);
        input.setTextSize(15);
        input.setPadding(dp(15), dp(5), dp(15), dp(5));
        input.setBackground(makeShape(
                Color.rgb(14, 19, 30),
                Color.rgb(35, 85, 140), 1
        ));

        return input;
    }

    private LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(
                width < 0 ? width : dp(width),
                height < 0 ? height : dp(height)
        );
    }

    private LinearLayout newPage() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(dp(28), dp(20), dp(28), dp(20));
        layout.setBackgroundColor(BG);
        return layout;
    }

    private void showLogin() {
        LinearLayout root = newPage();

        TextView logo = makeLabel(
                "CRASHAI NEO", 30, BLUE, true);
        root.addView(logo, lp(-1, 58));

        TextView subtitle = makeLabel(
                "CRASH INTELLIGENCE", 11, MUTED, false);
        root.addView(subtitle, lp(-1, 35));

        TextView welcome = makeLabel(
                "تسجيل الدخول", 21, Color.WHITE, true);

        LinearLayout.LayoutParams welcomeParams = lp(-1, 55);
        welcomeParams.topMargin = dp(20);
        root.addView(welcome, welcomeParams);

        EditText account = makeInput("معرّف حساب 1xBet");
        LinearLayout.LayoutParams accountParams = lp(-1, 54);
        accountParams.topMargin = dp(15);
        root.addView(account, accountParams);

        EditText pin = makeInput("رمز تطبيق CrashAI Neo");
        pin.setInputType(129);

        LinearLayout.LayoutParams pinParams = lp(-1, 54);
        pinParams.topMargin = dp(12);
        root.addView(pin, pinParams);

        TextView note = makeLabel(
                "رمز التطبيق للتحقق المحلي فقط",
                12, MUTED, false);

        LinearLayout.LayoutParams noteParams = lp(-1, 38);
        noteParams.topMargin = dp(5);
        root.addView(note, noteParams);

        TextView login = makeLabel(
                "دخول", 16, Color.WHITE, true);

        login.setBackground(makeShape(
                Color.rgb(13, 105, 200), BLUE, 1));

        LinearLayout.LayoutParams loginParams = lp(-1, 52);
        loginParams.topMargin = dp(15);
        root.addView(login, loginParams);

        login.setOnClickListener(v -> {
            if (account.getText().toString().trim().isEmpty()) {
                account.setError("أدخل معرّف الحساب");
                return;
            }

            if (!"sf.1999".equals(pin.getText().toString())) {
                pin.setError("رمز التطبيق غير صحيح");
                return;
            }

            showDashboard();
        });

        setContentView(root);
    }

    private void showDashboard() {
        LinearLayout root = newPage();

        TextView logo = makeLabel(
                "CRASHAI NEO",
                17,
                Color.rgb(95, 145, 200),
                true
        );

        root.addView(logo, lp(-1, 45));

        View topSpace = new View(this);
        root.addView(topSpace, lp(1, 30));

        // إطار واحد يحتوي الدائرة والنص؛ لا نضيف الدائرة إلى root
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);

        LinearLayout.LayoutParams frameParams = lp(-1, 220);
        frameParams.gravity = Gravity.CENTER;
        root.addView(frame, frameParams);

        TextView orb = makeLabel("", 22, Color.WHITE, true);

        orb.setBackground(makeCircle(
                Color.rgb(4, 15, 32),
                BLUE,
                3
        ));

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            orb.setElevation(dp(12));
        }

        FrameLayout.LayoutParams circleParams =
                new FrameLayout.LayoutParams(
                        dp(185), dp(185), Gravity.CENTER);

        frame.addView(orb, circleParams);

        // هالة نيون خارجية خفيفة
        View halo = new View(this);
        GradientDrawable haloDrawable = makeCircle(
                Color.TRANSPARENT,
                Color.rgb(15, 85, 190),
                2
        );
        halo.setBackground(haloDrawable);

        FrameLayout.LayoutParams haloParams =
                new FrameLayout.LayoutParams(
                        dp(201), dp(201), Gravity.CENTER);

        frame.addView(halo, haloParams);
        halo.bringToFront();

        TextView orbText = makeLabel(
                "CRASH", 22, Color.WHITE, true);

        FrameLayout.LayoutParams textParams =
                new FrameLayout.LayoutParams(
                        dp(150), dp(70), Gravity.CENTER);

        frame.addView(orbText, textParams);
        orbText.bringToFront();

        // نبض متكرر للدائرة
        ScaleAnimation pulse = new ScaleAnimation(
                0.96f, 1.04f,
                0.96f, 1.04f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );

        pulse.setDuration(1400);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);

        orb.startAnimation(pulse);

        ScaleAnimation haloPulse = new ScaleAnimation(
                0.94f, 1.07f,
                0.94f, 1.07f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );

        haloPulse.setDuration(1800);
        haloPulse.setRepeatMode(Animation.REVERSE);
        haloPulse.setRepeatCount(Animation.INFINITE);

        halo.startAnimation(haloPulse);

        View bottomSpace = new View(this);
        root.addView(bottomSpace, lp(1, 40));

        statusView = makeLabel(
                "جارٍ الاتصال", 11, MUTED, false);

        root.addView(statusView, lp(-1, 28));

        setContentView(root);

        connectToServer();
    }

    private void connectToServer() {
        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(API_URL);
                connection =
                        (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                int code = connection.getResponseCode();

                InputStream stream =
                        code >= 200 && code < 400
                                ? connection.getInputStream()
                                : connection.getErrorStream();

                StringBuilder body = new StringBuilder();

                if (stream != null) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(stream, "UTF-8"));

                    String line;

                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                    }

                    reader.close();
                    stream.close();
                }

                JSONObject response =
                        new JSONObject(body.toString());

                boolean healthy = code == 200
                        && "ok".equals(
                                response.optString("status"));

                runOnUiThread(() -> {
                    if (statusView != null) {
                        if (healthy) {
                            statusView.setText("● متصل");
                            statusView.setTextColor(
                                    Color.rgb(80, 220, 150));
                        } else {
                            statusView.setText(
                                    "● الخادم غير متاح");
                            statusView.setTextColor(
                                    Color.rgb(255, 100, 100));
                        }
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (statusView != null) {
                        statusView.setText(
                                "● انتظار الاتصال");
                        statusView.setTextColor(MUTED);
                    }
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }
}
