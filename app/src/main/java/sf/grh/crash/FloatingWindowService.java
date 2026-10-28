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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;

public class FloatingWindowService extends Service {

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "floating_overlay_channel";

    private WindowManager windowManager;
    private View floatingCircle;
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

        floatingCircle = new View(this);
        floatingCircle.setBackground(createCircle());

        int overlayType;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            overlayType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                circleSize,
                circleSize,
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

                        params.width = circleSize;
                        params.height = circleSize;

                        updateCirclePosition();
                        return true;
                    }
                }
        );

        floatingCircle.setOnTouchListener((view, event) -> {
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
            windowManager.addView(floatingCircle, params);
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
            if (floatingCircle.isAttachedToWindow()) {
                windowManager.updateViewLayout(floatingCircle, params);
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
        if (floatingCircle != null && windowManager != null) {
            try {
                if (floatingCircle.isAttachedToWindow()) {
                    windowManager.removeView(floatingCircle);
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
