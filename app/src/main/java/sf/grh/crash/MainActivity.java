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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int BG = Color.rgb(10, 16, 30);
    private static final int PANEL = Color.rgb(22, 32, 51);
    private static final int BLUE = Color.rgb(45, 158, 255);
    private static final int MUTED = Color.rgb(157, 180, 208);

    private static final String API_URL =
            "https://crashai-neo-api.onrender.com/analyze";

    private boolean waitingForOverlayPermission;

    private TextView connectionStatus;
    private TextView resultText;
    private EditText roundsInput;
    private Button analyzeButton;

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
                    + "من حساب 1xBet ولا يضمن نتائج اللعبة. "
                    + "يمكنك استخدام التحليل الإحصائي للنتائج السابقة."
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

        connectionStatus = label(
            "● جارٍ الاتصال بخادم التحليل...",
            12,
            Color.rgb(255, 194, 89),
            false
        );

        root.addView(connectionStatus, params(-1, 32));

        TextView orb = label(
            "CRASH",
            30,
            Color.WHITE,
            true
        );

        orb.setBackground(
            shape(Color.rgb(14, 48, 83), 140, BLUE, 3)
        );

        orb.setElevation(dp(8));

        LinearLayout.LayoutParams orbParams =
                params(190, 190);

        orbParams.gravity = Gravity.CENTER_HORIZONTAL;
        orbParams.topMargin = dp(12);
        orbParams.bottomMargin = dp(14);

        root.addView(orb, orbParams);

        root.addView(
            label("التحليل الإحصائي للجولات",
                18, Color.WHITE, true),
            params(-1, 40)
        );

        TextView instructions = label(
            "أدخل نتائج الجولات السابقة مفصولة بفواصل. "
            + "استخدم النقطة للأعداد العشرية.",
            13,
            MUTED,
            false
        );

        instructions.setGravity(Gravity.CENTER);
        root.addView(instructions, params(-1, 58));

        roundsInput = new EditText(this);
        roundsInput.setTextColor(Color.WHITE);
        roundsInput.setHintTextColor(Color.rgb(125, 145, 171));
        roundsInput.setHint("مثال: 1.25, 2.40, 1.08, 3.15");
        roundsInput.setTextSize(15);
        roundsInput.setGravity(Gravity.TOP | Gravity.START);
        roundsInput.setMinLines(3);
        roundsInput.setMaxLines(5);
        roundsInput.setPadding(
            dp(14), dp(12), dp(14), dp(12)
        );
        roundsInput.setBackground(
            shape(PANEL, 16, Color.rgb(52, 77, 110), 1)
        );

        root.addView(roundsInput, params(-1, 110));

        analyzeButton = button("تحليل النتائج  ←");

        LinearLayout.LayoutParams analyzeParams =
                params(-1, 54);

        analyzeParams.topMargin = dp(12);
        root.addView(analyzeButton, analyzeParams);

        resultText = label(
            "ستظهر إحصائيات النتائج هنا.",
            14,
            MUTED,
            false
        );

        resultText.setBackground(
            shape(PANEL, 18, Color.rgb(52, 77, 110), 1)
        );
        resultText.setPadding(
            dp(12), dp(12), dp(12), dp(12)
        );

        LinearLayout.LayoutParams resultParams =
                params(-1, -2);

        resultParams.topMargin = dp(14);
        root.addView(resultText, resultParams);

        analyzeButton.setOnClickListener(v -> analyzeRounds());

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
                    "يتصل التطبيق بخادم لتحليل إحصائيات "
                    + "النتائج التي تدخلها. هذا التحليل لا "
                    + "يتنبأ بنتيجة الجولة القادمة ولا يضمن الربح."
                )
                .setPositiveButton("حسناً", null)
                .show()
        );

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(BG);
        scrollView.addView(root);
        setContentView(scrollView);

        checkServer();
    }

    private void checkServer() {
        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(
                    "https://crashai-neo-api.onrender.com/health"
                );

                connection =
                    (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                int code = connection.getResponseCode();

                runOnUiThread(() -> {
                    if (connectionStatus == null) return;

                    if (code == 200) {
                        connectionStatus.setText(
                            "● متصل بخادم التحليل"
                        );
                        connectionStatus.setTextColor(
                            Color.rgb(80, 220, 140)
                        );
                    } else {
                        connectionStatus.setText(
                            "● الخادم لا يستجيب حالياً"
                        );
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (connectionStatus != null) {
                        connectionStatus.setText(
                            "● تعذّر الاتصال بالخادم"
                        );
                        connectionStatus.setTextColor(
                            Color.rgb(255, 120, 100)
                        );
                    }
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private void analyzeRounds() {
        String raw = roundsInput.getText().toString().trim();

        if (raw.isEmpty()) {
            roundsInput.setError("أدخل نتائج الجولات أولاً");
            return;
        }

        String[] parts = raw.split("[,،;؛\\s]+");

        if (parts.length > 500) {
            roundsInput.setError(
                "أدخل 500 نتيجة أو أقل في كل مرة"
            );
            return;
        }

        ArrayList<Double> values = new ArrayList<>();

        try {
            for (String part : parts) {
                if (part.trim().isEmpty()) continue;

                double value = Double.parseDouble(part.trim());

                if (Double.isNaN(value)
                        || Double.isInfinite(value)
                        || value <= 0) {
                    throw new NumberFormatException();
                }

                values.add(value);
            }
        } catch (NumberFormatException e) {
            roundsInput.setError(
                "تحقق من الأرقام. مثال: 1.25, 2.40, 3.10"
            );
            return;
        }

        if (values.isEmpty()) {
            roundsInput.setError("لم يتم العثور على أرقام صحيحة");
            return;
        }

        JSONArray rounds = new JSONArray();

        for (Double value : values) {
            rounds.put(value);
        }

        JSONObject requestBody = new JSONObject();

        try {
            requestBody.put("rounds", rounds);
        } catch (Exception e) {
            showError("تعذّر تجهيز البيانات");
            return;
        }

        analyzeButton.setEnabled(false);
        analyzeButton.setText("جارٍ التحليل...");
        resultText.setText("يتم إرسال النتائج إلى خادم التحليل...");
        connectionStatus.setText("● جارٍ تحليل البيانات");

        final String jsonBody = requestBody.toString();

        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(API_URL);

                connection =
                    (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(20000);
                connection.setDoOutput(true);
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                );
                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                );

                byte[] data = jsonBody.getBytes("UTF-8");

                try (OutputStream output =
                        connection.getOutputStream()) {
                    output.write(data);
                    output.flush();
                }

                int code = connection.getResponseCode();

                InputStream stream =
                    (code >= 200 && code < 400)
                    ? connection.getInputStream()
                    : connection.getErrorStream();

                StringBuilder response = new StringBuilder();

                if (stream != null) {
                    try (BufferedReader reader =
                            new BufferedReader(
                                new InputStreamReader(
                                    stream, "UTF-8"
                                ))) {

                        String line;

                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                    }
                }

                if (code < 200 || code >= 300) {
                    throw new Exception(
                        "استجاب الخادم برمز: " + code
                    );
                }

                JSONObject result =
                    new JSONObject(response.toString());

                int count = result.optInt("count", values.size());
                double mean = result.optDouble("mean", 0);
                double min = result.optDouble("min", 0);
                double max = result.optDouble("max", 0);

                String message =
                    "تم تحليل النتائج بنجاح\n\n"
                    + "عدد الجولات: " + count + "\n"
                    + "المتوسط: "
                    + String.format(Locale.US, "%.2f", mean) + "x\n"
                    + "أدنى نتيجة: "
                    + String.format(Locale.US, "%.2f", min) + "x\n"
                    + "أعلى نتيجة: "
                    + String.format(Locale.US, "%.2f", max) + "x\n\n"
                    + "تنبيه: هذه إحصائيات للنتائج السابقة فقط، "
                    + "وليست تنبؤاً بالجولة القادمة.";

                runOnUiThread(() -> {
                    resultText.setText(message);

                    connectionStatus.setText(
                        "● متصل بخادم التحليل"
                    );
                    connectionStatus.setTextColor(
                        Color.rgb(80, 220, 140)
                    );

                    analyzeButton.setEnabled(true);
                    analyzeButton.setText("تحليل النتائج  ←");
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    resultText.setText(
                        "تعذّر تحليل النتائج.\n"
                        + "تحقق من اتصال الإنترنت وحاول مجدداً.\n\n"
                        + "قد يحتاج الخادم المجاني إلى بعض الوقت "
                        + "ليبدأ العمل إذا كان متوقفاً مؤقتاً."
                    );

                    connectionStatus.setText(
                        "● فشل الاتصال أو التحليل"
                    );
                    connectionStatus.setTextColor(
                        Color.rgb(255, 120, 100)
                    );

                    analyzeButton.setEnabled(true);
                    analyzeButton.setText("إعادة المحاولة");
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private void showError(String message) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show();
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
