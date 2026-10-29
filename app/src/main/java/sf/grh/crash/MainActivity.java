package sf.grh.crash;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.media.projection.MediaProjectionManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import org.json.JSONObject;

public class MainActivity extends Activity {

    private static final int BG = Color.rgb(3, 5, 10);
    private static final int BLUE = Color.rgb(30, 145, 255);
    private static final int MUTED = Color.rgb(120, 145, 175);

    private static final int OVERLAY_PERMISSION_REQUEST = 701;
    private static final int CAPTURE_PERMISSION_REQUEST = 702;
    private TextView analysisLabel;

    private static final String PREFS_NAME = "crashai_session";
    private static final String KEY_LOGGED_IN = "verified_login_v2";

    private SharedPreferences preferences;

    private int dp(float value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        preferences = getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
        );

        if (!hasInternet()) {
            showOffline();
        } else if (preferences.getBoolean(KEY_LOGGED_IN, false)) {
            showDashboard();
        } else {
            showLogin();
        }
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
                Color.rgb(35, 85, 140),
                1
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

    private boolean hasInternet() {
        ConnectivityManager manager = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo network = manager == null ? null : manager.getActiveNetworkInfo();
        return network != null && network.isConnected();
    }

    private void showOffline() {
        LinearLayout root = newPage();
        TextView message = makeLabel("CrashAI Neo\\nيلزم اتصال بالإنترنت للتشغيل", 19, Color.WHITE, true);
        root.addView(message, lp(-1, 140));
        TextView retry = makeLabel("إعادة المحاولة", 17, BLUE, true);
        root.addView(retry, lp(-1, 60));
        retry.setOnClickListener(v -> {
            if (!hasInternet()) {
                Toast.makeText(this, "لا يوجد اتصال بالإنترنت", Toast.LENGTH_SHORT).show();
            } else if (preferences.getBoolean(KEY_LOGGED_IN, false)) showDashboard();
            else showLogin();
        });
        setContentView(root);
    }

    private void showLogin() {
        if (!hasInternet()) { showOffline(); return; }
        LinearLayout root = newPage();

        TextView logo = makeLabel(
                "CRASHAI NEO", 30, BLUE, true
        );
        root.addView(logo, lp(-1, 58));

        TextView subtitle = makeLabel(
                "CRASH INTELLIGENCE", 11, MUTED, false
        );
        root.addView(subtitle, lp(-1, 35));

        TextView welcome = makeLabel(
                "تسجيل الدخول", 21, Color.WHITE, true
        );

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
                12,
                MUTED,
                false
        );

        LinearLayout.LayoutParams noteParams = lp(-1, 38);
        noteParams.topMargin = dp(5);
        root.addView(note, noteParams);

        TextView login = makeLabel(
                "دخول", 16, Color.WHITE, true
        );
        login.setBackground(makeShape(BLUE, BLUE, 1));

        LinearLayout.LayoutParams loginParams = lp(-1, 52);
        loginParams.topMargin = dp(18);
        root.addView(login, loginParams);

        login.setOnClickListener(v -> {
            String accountText = account.getText().toString().trim();
            String pinText = pin.getText().toString();
            if (accountText.isEmpty() || pinText.isEmpty()) {
                Toast.makeText(this, "أدخل المعرّف والرمز", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!hasInternet()) { showOffline(); return; }
            login.setEnabled(false);
            login.setText("جارٍ التحقق...");
            new Thread(() -> {
                HttpURLConnection connection = null;
                boolean authorized = false;
                try {
                    URL url = new URL("https://crashai-neo-api.onrender.com/login");
                    connection = (HttpURLConnection)url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(4000);
                    connection.setReadTimeout(4000);
                    connection.setRequestProperty("Content-Type", "application/json");
                    JSONObject body = new JSONObject();
                    body.put("account_id", accountText);
                    body.put("app_code", pinText);
                    try (OutputStream output = connection.getOutputStream()) {
                        output.write(body.toString().getBytes("UTF-8"));
                    }
                    if (connection.getResponseCode() == 200) {
                        authorized = true;
                    }
                } catch (Exception ignored) {
                } finally {
                    if (connection != null) connection.disconnect();
                }
                final boolean success = authorized;
                runOnUiThread(() -> {
                    login.setEnabled(true);
                    login.setText("دخول");
                    if (success) {
                        preferences.edit().putBoolean(KEY_LOGGED_IN, true).apply();
                        showDashboard();
                    } else {
                        Toast.makeText(this, "البيانات غير صحيحة أو الخادم غير متاح", Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        });

        setContentView(root);
    }

    private void showDashboard() {
        if (!hasInternet()) { showOffline(); return; }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        // زر تشغيل النافذة العائمة أعلى اليسار.
        TextView overlayButton = makeLabel(
                "◉", 22, BLUE, true
        );

        FrameLayout.LayoutParams buttonParams =
                new FrameLayout.LayoutParams(dp(48), dp(48));

        buttonParams.gravity = Gravity.TOP | Gravity.START;
        buttonParams.leftMargin = dp(10);
        buttonParams.topMargin = dp(10);

        overlayButton.setBackground(makeCircle(
                Color.rgb(10, 18, 32),
                Color.rgb(20, 100, 180),
                1
        ));

        root.addView(overlayButton, buttonParams);

        overlayButton.setOnClickListener(
                v -> requestOverlayPermission()
        );

        // زر OUT الصغير أعلى اليمين.
        TextView logoutButton = makeLabel(
                "OUT", 12, BLUE, true
        );

        FrameLayout.LayoutParams logoutParams =
                new FrameLayout.LayoutParams(dp(52), dp(36));

        logoutParams.gravity = Gravity.TOP | Gravity.END;
        logoutParams.rightMargin = dp(12);
        logoutParams.topMargin = dp(16);

        logoutButton.setBackground(makeShape(
                Color.rgb(10, 18, 32),
                Color.rgb(20, 100, 180),
                1
        ));

        root.addView(logoutButton, logoutParams);

        logoutButton.setOnClickListener(v -> logout());

        // الدائرة الرئيسية المتوهجة.
        FrameLayout center = new FrameLayout(this);

        FrameLayout.LayoutParams centerParams =
                new FrameLayout.LayoutParams(dp(230), dp(230));

        centerParams.gravity = Gravity.CENTER;

        View outerGlow = new View(this);

        outerGlow.setBackground(makeCircle(
                Color.rgb(7, 20, 38),
                Color.rgb(15, 90, 170),
                2
        ));

        FrameLayout.LayoutParams glowParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        center.addView(outerGlow, glowParams);

        View innerCircle = new View(this);

        innerCircle.setBackground(makeCircle(
                Color.rgb(3, 8, 16),
                BLUE,
                3
        ));

        FrameLayout.LayoutParams innerParams =
                new FrameLayout.LayoutParams(dp(175), dp(175));

        innerParams.gravity = Gravity.CENTER;

        center.addView(innerCircle, innerParams);

        analysisLabel = makeLabel(AnalysisState.current(this), 14, BLUE, true);
        TextView core = analysisLabel;

        FrameLayout.LayoutParams coreParams =
                new FrameLayout.LayoutParams(dp(100), dp(100));

        coreParams.gravity = Gravity.CENTER;

        center.addView(core, coreParams);

        Animation pulse = new ScaleAnimation(
                0.96f, 1.04f,
                0.96f, 1.04f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );

        pulse.setDuration(1400);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);

        center.startAnimation(pulse);

        root.addView(center, centerParams);

        TextView captureButton = makeLabel("بدء قراءة الشاشة", 16, Color.WHITE, true);
        captureButton.setBackground(makeShape(BLUE, BLUE, 1));
        FrameLayout.LayoutParams captureParams = new FrameLayout.LayoutParams(dp(220), dp(50));
        captureParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        captureParams.bottomMargin = dp(85);
        root.addView(captureButton, captureParams);
        captureButton.setOnClickListener(v -> requestScreenCapture());

        TextView stopButton = makeLabel("إيقاف المراقبة", 13, MUTED, false);
        FrameLayout.LayoutParams stopParams = new FrameLayout.LayoutParams(dp(200), dp(44));
        stopParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        stopParams.bottomMargin = dp(30);
        root.addView(stopButton, stopParams);
        stopButton.setOnClickListener(v -> stopService(new Intent(this, ScreenCaptureService.class)));

        setContentView(root);

        checkServerHealth();
    }

    private void logout() {
        // إيقاف خدمة الدائرة العائمة.
        Intent serviceIntent = new Intent(
                MainActivity.this,
                FloatingWindowService.class
        );

        stopService(serviceIntent);
        stopService(new Intent(this, ScreenCaptureService.class));

        // مسح حالة الجلسة المحفوظة.
        preferences.edit()
                .remove(KEY_LOGGED_IN)
                .apply();

        // العودة إلى شاشة تسجيل الدخول.
        showLogin();

        Toast.makeText(
                this,
                "تم تسجيل الخروج وإيقاف الدائرة العائمة",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {

            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );

            startActivityForResult(
                    intent,
                    OVERLAY_PERMISSION_REQUEST
            );

        } else {
            startFloatingService();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAPTURE_PERMISSION_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                Intent capture = new Intent(this, ScreenCaptureService.class);
                capture.putExtra("result_code", resultCode);
                capture.putExtra("projection_data", data);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(capture);
                else startService(capture);
            } else {
                Toast.makeText(this, "يمكنك استخدام التطبيق بدون تسجيل الشاشة", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                    || Settings.canDrawOverlays(this)) {

                startFloatingService();

            } else {
                Toast.makeText(
                        this,
                        "يجب السماح بالظهور فوق التطبيقات",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    private void requestScreenCapture() {
        if (!hasInternet()) { showOffline(); return; }
        MediaProjectionManager manager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), CAPTURE_PERMISSION_REQUEST);
    }

    @Override protected void onResume() {
        super.onResume();
        if (analysisLabel != null) analysisLabel.setText(AnalysisState.current(this));
    }

    private void startFloatingService() {
        if (!hasInternet()) { showOffline(); return; }
        Intent intent = new Intent(
                MainActivity.this,
                FloatingWindowService.class
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }

            Toast.makeText(
                    this,
                    "تم طلب تشغيل الدائرة العائمة",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "تعذر تشغيل الدائرة: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void checkServerHealth() {
        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(
                        "https://crashai-neo-api.onrender.com/health"
                );

                connection = (HttpURLConnection)
                        url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);

                int code = connection.getResponseCode();

                runOnUiThread(() -> {
                    if (code == 200) {
                        Toast.makeText(
                                MainActivity.this,
                                "اتصال الخادم ناجح",
                                Toast.LENGTH_SHORT
                        ).show();
                    } else {
                        Toast.makeText(
                                MainActivity.this,
                                "الخادم أعاد الرمز: " + code,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "تعذر الاتصال بالخادم",
                        Toast.LENGTH_SHORT
                ).show());

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }
}
