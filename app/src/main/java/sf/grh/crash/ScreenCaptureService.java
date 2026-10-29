package sf.grh.crash;

import android.app.*;
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
    private static final Pattern MULTIPLIER = Pattern.compile("(\\d{1,6}[.,]\\d{1,3})\\s*[x×]", Pattern.CASE_INSENSITIVE);
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
    private long lastFrameAt;

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "CrashAI screen analysis",
                    NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
        Notification notification = new Notification.Builder(this, Build.VERSION.SDK_INT >= 26 ? CHANNEL : "")
                .setContentTitle("CrashAI Neo")
                .setContentText("تحليل الشاشة جارٍ — أوقفه من التطبيق")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1002, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else startForeground(1002, notification);
        workerThread = new HandlerThread("crashai-capture");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
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
            AnalysisState.update(this, "مراقبة الشاشة");
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
                    .addOnSuccessListener(result -> interpret(result.getText()))
                    .addOnFailureListener(e -> AnalysisState.update(this, "خطأ في قراءة الشاشة"))
                    .addOnCompleteListener(task -> { processing = false; frame.recycle(); });
        } catch (Exception e) {
            processing = false;
        } finally {
            if (image != null) image.close();
        }
    }

    private void interpret(String text) {
        // Avoid treating a live in-flight multiplier as a final round.
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        boolean countdown = lower.contains("place your bets") || lower.contains("ضع رهان") || lower.contains("next round");
        boolean finished = lower.contains("flew away") || lower.contains("crashed") || lower.contains("طار") || lower.contains("انفجر");
        Matcher match = MULTIPLIER.matcher(text);
        String found = null;
        while (match.find()) found = match.group(1).replace(',', '.');
        if (countdown) phase = "countdown";
        else if (finished) phase = "finished";
        else if (found != null) phase = "flying";
        if (found == null) {
            if (countdown) AnalysisState.update(this, "العد التنازلي");
            return;
        }
        if (found.equals(previousReading)) sameReadings++;
        else { previousReading = found; sameReadings = 1; }
        AnalysisState.update(this, (finished ? "نهاية " : "مرصود ") + found + "x");
        // A final marker and two agreeing frames are required; no invented predictions.
        if (finished && sameReadings >= 2 && (!found.equals(lastRecorded) ||
                SystemClock.elapsedRealtime() - lastRecordedAt > 12000)) {
            lastRecorded = found;
            lastRecordedAt = SystemClock.elapsedRealtime();
            final String finalValue = found;
            worker.post(() -> sendConfirmed(finalValue));
        }
    }

    private void sendConfirmed(String multiplier) {
        HttpURLConnection connection = null;
        try {
            double value = Double.parseDouble(multiplier);
            JSONObject body = new JSONObject();
            body.put("round_id", "screen-" + System.currentTimeMillis());
            body.put("final_multiplier", value);
            URL url = new URL("https://crashai-neo-api.onrender.com/learn-round");
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(1400);
            connection.setReadTimeout(1400);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            if (connection.getResponseCode() / 100 == 2) {
                AnalysisState.update(this, "تم تسجيل " + multiplier + "x");
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
