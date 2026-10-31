package sf.grh.crash;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reads the public, read-only statistical report. It does not place bets or access game internals. */
final class IntelligenceClient {
    private static final String ENDPOINT =
            "https://crashai-neo-api.onrender.com/intelligence?limit=500";
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Callback {
        void onResult(String text);
    }

    private IntelligenceClient() {}

    static void fetch(Callback callback) {
        if (!IN_FLIGHT.compareAndSet(false, true)) return;
        new Thread(() -> {
            HttpURLConnection connection = null;
            String report;
            try {
                connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("Accept", "application/json");
                int code = connection.getResponseCode();
                if (code != 200) {
                    report = "NEO AI: الخادم غير جاهز (HTTP " + code + ")";
                } else {
                    try (InputStream input = connection.getInputStream();
                         BufferedReader reader = new BufferedReader(
                                 new InputStreamReader(input, StandardCharsets.UTF_8))) {
                        StringBuilder body = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) body.append(line);
                        JSONObject json = new JSONObject(body.toString());
                        int samples = json.optInt("sample_count", 0);
                        int tests = json.optInt("walk_forward_tests", 0);
                        String model = json.optString("selected_model", "غير محدد");
                        boolean edge = json.optBoolean("validated_edge", false);
                        String status = samples < 60
                                ? "بيانات غير كافية"
                                : (edge ? "أفضلية تاريخية مختبرة" : "لا توجد أفضلية مثبتة");
                        report = "NEO AI: " + status + " | بيانات " + samples
                                + " | اختبارات " + tests + " | " + model;
                        if (samples >= 60 && json.has("point_forecast")) {
                            report += " | تقدير وصفي " +
                                    String.format(java.util.Locale.US, "%.2fx",
                                            json.optDouble("point_forecast", 0));
                        }
                    }
                }
            } catch (Exception error) {
                report = "NEO AI: تعذر جلب التحليل";
            } finally {
                if (connection != null) connection.disconnect();
                IN_FLIGHT.set(false);
            }
            final String result = report;
            MAIN.post(() -> callback.onResult(result));
        }, "crashai-intelligence").start();
    }
}
