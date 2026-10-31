package sf.grh.crash;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;

public class FloatingWindowService extends Service {

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "floating_overlay_channel";

    private WindowManager windowManager;
    private TextView floatingCircle;
    private TextView recordedLabel;
    private TextView diagnosticLabel;
    private TextView intelligenceLabel;
    private final Handler intelligenceHandler = new Handler(Looper.getMainLooper());
    private final Runnable intelligenceRefresh = new Runnable() {
        @Override public void run() {
            IntelligenceClient.fetch(result -> {
                if (intelligenceLabel != null) intelligenceLabel.setText(result);
            });
            intelligenceHandler.postDelayed(this, 60000);
        }
    };
    private LinearLayout overlayRoot;
    private final Handler displayHandler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (floatingCircle != null) floatingCircle.setText(AnalysisState.current(FloatingWindowService.this));
            if (recordedLabel != null) recordedLabel.setText(AnalysisState.lastRecorded(FloatingWindowService.this));
            if (diagnosticLabel != null) diagnosticLabel.setText(AnalysisState.status(FloatingWindowService.this));
            displayHandler.postDelayed(this, 700);
        }
    };
    private WindowManager.LayoutParams params;
    private ScaleGestureDetector scaleDetector;

    private int circleSize = 90;
    private float initialTouchX;
    private float initialTouchY;
    private int initialX;
    private int initialY;

    @Override
    public void onCreate() {
        super.onCreate();

        startAsForegroundService();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !android.provider.Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        overlayRoot = new LinearLayout(this);
        overlayRoot.setOrientation(LinearLayout.VERTICAL);
        overlayRoot.setGravity(Gravity.CENTER);
        recordedLabel = new TextView(this);
        recordedLabel.setText(AnalysisState.lastRecorded(this));
        recordedLabel.setTextColor(Color.rgb(110,235,255));
        recordedLabel.setTextSize(13);
        recordedLabel.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        recordedLabel.setGravity(Gravity.CENTER);
        recordedLabel.setShadowLayer(dp(9),0,0,Color.CYAN);
        recordedLabel.setSingleLine(true);
        overlayRoot.addView(recordedLabel,new LinearLayout.LayoutParams(dp(205),dp(34)));
        diagnosticLabel = new TextView(this);
        diagnosticLabel.setTextColor(Color.LTGRAY);
        diagnosticLabel.setTextSize(10);
        diagnosticLabel.setGravity(Gravity.CENTER);
        diagnosticLabel.setMaxLines(2);
        overlayRoot.addView(diagnosticLabel, new LinearLayout.LayoutParams(dp(210), dp(42)));
        intelligenceLabel = new TextView(this);
        intelligenceLabel.setText("NEO AI: جارٍ تحميل التحليل...");
        intelligenceLabel.setTextColor(Color.rgb(255, 190, 90));
        intelligenceLabel.setTextSize(10);
        intelligenceLabel.setGravity(Gravity.CENTER);
        intelligenceLabel.setMaxLines(2);
        overlayRoot.addView(intelligenceLabel, new LinearLayout.LayoutParams(dp(210), dp(34)));
        floatingCircle = new TextView(this);
        floatingCircle.setText(AnalysisState.current(this));
        floatingCircle.setTextColor(Color.WHITE);
        floatingCircle.setTextSize(22);
        floatingCircle.setGravity(Gravity.CENTER);
        floatingCircle.setPadding(dp(5), dp(5), dp(5), dp(5));
        floatingCircle.setBackground(createCircle());
        overlayRoot.addView(floatingCircle,new LinearLayout.LayoutParams(dp(90),dp(90)));

        int overlayType;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            overlayType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                dp(210),
                dp(110) + dp(circleSize),
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 40;
        params.y = 180;

        scaleDetector = new ScaleGestureDetector(
                this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        circleSize = Math.max(
                                50,
                                Math.min(
                                        220,
                                        (int) (circleSize
                                                * detector.getScaleFactor())
                                )
                        );

                        floatingCircle.getLayoutParams().width = dp(circleSize);
                        floatingCircle.getLayoutParams().height = dp(circleSize);
                        overlayRoot.requestLayout();
                        params.height = dp(110 + circleSize);

                        updateCirclePosition();
                        return true;
                    }
                }
        );

        overlayRoot.setOnTouchListener((view, event) -> {
            scaleDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() == 1
                            && !scaleDetector.isInProgress()) {
                        params.x = initialX
                                + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY
                                + (int) (event.getRawY() - initialTouchY);

                        updateCirclePosition();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    return true;

                default:
                    return true;
            }
        });

        try {
            windowManager.addView(overlayRoot, params);
            displayHandler.post(refresh);
            intelligenceHandler.post(intelligenceRefresh);
        } catch (Exception e) {
            stopSelf();
        }
    }

    private GradientDrawable createCircle() {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.TRANSPARENT);
        circle.setStroke(dp(4), Color.rgb(0, 190, 255));
        return circle;
    }

    private int dp(float value) {
        return (int) (value
                * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void updateCirclePosition() {
        if (windowManager == null || floatingCircle == null) {
            return;
        }

        try {
            if (overlayRoot.isAttachedToWindow()) {
                windowManager.updateViewLayout(overlayRoot, params);
            }
        } catch (Exception ignored) {
        }
    }

    private void startAsForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Floating overlay",
                    NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager manager =
                    (NotificationManager) getSystemService(
                            NOTIFICATION_SERVICE
                    );

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
                .setContentTitle("CrashAI Neo")
                .setContentText("الدائرة العائمة قيد التشغيل")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        displayHandler.removeCallbacks(refresh);
        intelligenceHandler.removeCallbacks(intelligenceRefresh);
        if (floatingCircle != null && windowManager != null) {
            try {
                if (floatingCircle.isAttachedToWindow()) {
                    windowManager.removeView(overlayRoot);
                }
            } catch (Exception ignored) {
            }
        }

        floatingCircle = null;
        windowManager = null;

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
