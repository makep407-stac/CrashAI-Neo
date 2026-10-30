package sf.grh.crash;

import android.app.*;
import android.app.PendingIntent;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONObject;
import org.json.JSONArray;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScreenCaptureService extends Service {
    public static final String ACTION_STOP = "sf.grh.crash.STOP_CAPTURE";
    private static final String CHANNEL = "crashai_capture";
    private static final Pattern MULTIPLIER = Pattern.compile("(\\d{1,6}[.,]\\d{1,3})\\s*[x×]?", Pattern.CASE_INSENSITIVE);
    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread workerThread;
    private Handler worker;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private TextRecognizer recognizer;
    private boolean processing;
    private String previousReading = "";
    private int sameReadings;
    private String lastRecorded = "";
    private long lastRecordedAt;
    private String phase = "unknown";
    private String lastLiveReading = "";
    private long lastFrameAt;
    private String currentRoundId = "";

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "CrashAI screen analysis",
                    NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
        Notification.Builder notificationBuilder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        Notification notification = notificationBuilder
                .setContentTitle("CrashAI Neo")
                .setContentText("قراءة الشاشة تعمل — يمكنك إيقافها من هنا")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .addAction(android.R.drawable.ic_media_pause, "إيقاف القراءة", stopCapturePendingIntent())
                .setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1002, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else startForeground(1002, notification);
        workerThread = new HandlerThread("crashai-capture");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    private PendingIntent stopCapturePendingIntent() {
        Intent stopIntent = new Intent(this, ScreenCaptureService.class);
        stopIntent.setAction(ACTION_STOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, 1003, stopIntent, flags);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        int result = intent.getIntExtra("result_code", Activity.RESULT_CANCELED);
        Intent data = intent.getParcelableExtra("projection_data");
        if (result != Activity.RESULT_OK || data == null) {
            stopSelf(); return START_NOT_STICKY;
        }
        try {
            MediaProjectionManager manager = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(result, data);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopSelf(); }
            }, main);
            DisplayMetrics metrics = new DisplayMetrics();
            ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(metrics);
            int width = metrics.widthPixels, height = metrics.heightPixels;
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            reader.setOnImageAvailableListener(r -> capture(r), worker);
            display = projection.createVirtualDisplay("CrashAI screen", width, height,
                    metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(), null, worker);
            AnalysisState.update(this, "مراقبة الشاشة — جمع أول 20 جولة");
        } catch (Exception e) {
            AnalysisState.update(this, "تعذر التقاط الشاشة");
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void capture(ImageReader source) {
        Image image = null;
        try {
            image = source.acquireLatestImage();
            if (image == null) return;
            long now = SystemClock.elapsedRealtime();
            if (processing || now - lastFrameAt < 650) return;
            lastFrameAt = now;
            Image.Plane plane = image.getPlanes()[0];
            java.nio.ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int width = image.getWidth(), height = image.getHeight();
            Bitmap padded = Bitmap.createBitmap(rowStride / pixelStride, height, Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(buffer);
            Bitmap frame = Bitmap.createBitmap(padded, 0, 0, width, height);
            padded.recycle();
            processing = true;
            recognizer.process(InputImage.fromBitmap(frame, 0))
                    .addOnSuccessListener(result -> interpret(result))
                    .addOnFailureListener(e -> AnalysisState.update(this, "خطأ في قراءة الشاشة"))
                    .addOnCompleteListener(task -> { processing = false; frame.recycle(); });
        } catch (Exception e) {
            processing = false;
        } finally {
            if (image != null) image.close();
        }
    }

    private void interpret(com.google.mlkit.vision.text.Text textResult) {
        String text = textResult.getText();
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        boolean countdown = lower.contains("place your bets") || lower.contains("place your bet")
                || lower.contains("bets are open") || lower.contains("bets are closed")
                || lower.contains("starting in") || lower.contains("starts in")
                || lower.contains("next round") || lower.contains("waiting for next round")
                || lower.contains("ضع رهان") || lower.contains("ضع رهانات")
                || lower.contains("الرهانات") || lower.contains("الجولة القادمة")
                || lower.contains("تبدأ خلال") || lower.contains("بدء الجولة")
                || hasCountdownNumber(textResult);
        boolean finished = lower.contains("flew away") || lower.contains("crashed")
                || lower.contains("طار") || lower.contains("انفجر");
        String found = null;
        long largestArea = -1;
        for (com.google.mlkit.vision.text.Text.TextBlock block : textResult.getTextBlocks()) {
            for (com.google.mlkit.vision.text.Text.Line line : block.getLines()) {
                Matcher lineMatch = MULTIPLIER.matcher(line.getText());
                while (lineMatch.find()) {
                    android.graphics.Rect bounds = line.getBoundingBox();
                    long area = bounds == null ? 0L : (long) bounds.width() * bounds.height();
                    if (area > largestArea) {
                        largestArea = area;
                        found = lineMatch.group(1).replace(',', '.');
                    }
                }
            }
        }

        String priorPhase = phase;
        if (countdown) {
            phase = "countdown";
            if ("flying".equals(priorPhase) && !lastLiveReading.isEmpty()
                    && !lastLiveReading.equals(lastRecorded)) {
                lastRecorded = lastLiveReading;
                lastRecordedAt = SystemClock.elapsedRealtime();
                final String completed = lastLiveReading;
                final String completedRoundId = currentRoundId;
                currentRoundId = "";
                worker.post(() -> sendConfirmed(completed, completedRoundId));
            }
            if (currentRoundId.isEmpty()) {
                currentRoundId = "screen-" + System.currentTimeMillis();
                final String upcomingId = currentRoundId;
                worker.post(() -> requestForecast(upcomingId));
            }
            lastLiveReading = "";
            previousReading = "";
            sameReadings = 0;
            return;
        }

        if (found == null) {
            if (finished && "flying".equals(priorPhase) && !lastLiveReading.isEmpty()
                    && !lastLiveReading.equals(lastRecorded)) {
                lastRecorded = lastLiveReading;
                lastRecordedAt = SystemClock.elapsedRealtime();
                final String completed = lastLiveReading;
                final String completedRoundId = currentRoundId;
                worker.post(() -> sendConfirmed(completed, completedRoundId));
                currentRoundId = "";
                phase = "finished";
                lastLiveReading = "";
            }
            previousReading = "";
            sameReadings = 0;
            return;
        }

        phase = finished ? "finished" : "flying";
        if (found.equals(previousReading)) sameReadings++;
        else { previousReading = found; sameReadings = 1; }
        if (sameReadings >= 2) lastLiveReading = found;

        // If the end marker appears with the final multiplier, wait for two matching frames.
        if (finished && sameReadings >= 2 && !found.equals(lastRecorded)) {
            lastRecorded = found;
            lastRecordedAt = SystemClock.elapsedRealtime();
            final String completed = found;
            final String completedRoundId = currentRoundId;
            worker.post(() -> sendConfirmed(completed, completedRoundId));
            currentRoundId = "";
            lastLiveReading = "";
        }
    }

    // Recognize an explicit countdown only when OCR exposes a countdown label and a
    // short standalone digit in the same screen; avoids treating multiplier digits as a timer.
    private boolean hasCountdownNumber(com.google.mlkit.vision.text.Text result) {
        String all = result.getText().toLowerCase(java.util.Locale.ROOT);
        boolean timerContext = all.contains("sec") || all.contains("second")
                || all.contains("countdown") || all.contains("starts in")
                || all.contains("starting in") || all.contains("ثانية")
                || all.contains("ثوان") || all.contains("العد التنازلي");
        if (!timerContext) return false;
        for (com.google.mlkit.vision.text.Text.TextBlock block : result.getTextBlocks()) {
            for (com.google.mlkit.vision.text.Text.Line line : block.getLines()) {
                String value = line.getText().trim();
                if (value.matches("[1-5]")) return true;
            }
        }
        return false;
    }

    private void requestForecast(String roundId) {
        HttpURLConnection connection = null;
        try {
            JSONObject body = new JSONObject();
            body.put("round_id", roundId);
            connection = (HttpURLConnection) new URL("https://crashai-neo-api.onrender.com/prepare-round").openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            if (connection.getResponseCode() == 200) {
                StringBuilder json = new StringBuilder();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null) json.append(line);
                }
                JSONObject parsed = new JSONObject(json.toString());
                if (parsed.optBoolean("available", false) && roundId.equals(currentRoundId)) {
                    AnalysisState.updateMultiplier(this, parsed.getDouble("estimate_multiplier"), parsed.optInt("sample_count", 0));
                    AnalysisState.update(this, "تقدير إحصائي جاهز للجولة");
                } else if (!parsed.optBoolean("available", false)) {
                    AnalysisState.update(this, "الخادم سجل " + parsed.optInt("sample_count", 0) + " من 20 جولة مطلوبة");
                }
            } else {
                AnalysisState.update(this, "طلب التقدير فشل: HTTP " + connection.getResponseCode());
            }
        } catch (Exception e) {
            AnalysisState.update(this, "تعذر جلب التقدير — تحقق من اتصال الخادم");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
    private void sendConfirmed(String multiplier, String roundId) {
        HttpURLConnection connection = null;
        try {
            double value = Double.parseDouble(multiplier);
            JSONObject body = new JSONObject();
            body.put("round_id", roundId == null || roundId.isEmpty() ? "screen-" + System.currentTimeMillis() : roundId);
            body.put("final_multiplier", value);
            URL url = new URL("https://crashai-neo-api.onrender.com/learn-round");
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            if (connection.getResponseCode() / 100 == 2) {
                AnalysisState.update(this, "تم تسجيل " + multiplier + "x");
                HttpURLConnection stats = null;
                try {
                    stats = (HttpURLConnection) new URL(
                            "https://crashai-neo-api.onrender.com/next-estimate?limit=200"
                    ).openConnection();
                    stats.setConnectTimeout(10000);
                    stats.setReadTimeout(10000);
                    if (stats.getResponseCode() == 200) {
                        StringBuilder json = new StringBuilder();
                        try (BufferedReader in = new BufferedReader(
                                new InputStreamReader(stats.getInputStream(),
                                        java.nio.charset.StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = in.readLine()) != null) json.append(line);
                        }
                        JSONObject parsed = new JSONObject(json.toString());
                        if (parsed.optBoolean("available", false)) {
                            AnalysisState.updateMultiplier(this,
                                    parsed.getDouble("estimate_multiplier"),
                                    parsed.getInt("sample_count"));
                        }
                    }
                } finally {
                    if (stats != null) stats.disconnect();
                }
            }
        } catch (Exception ignored) {
            AnalysisState.update(this, "قراءة " + multiplier + "x — الخادم غير متاح");
        } finally { if (connection != null) connection.disconnect(); }
    }

    @Override public void onDestroy() {
        if (reader != null) reader.close();
        if (display != null) display.release();
        if (projection != null) projection.stop();
        if (recognizer != null) recognizer.close();
        if (workerThread != null) workerThread.quitSafely();
        AnalysisState.update(this, "المراقبة متوقفة");
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
