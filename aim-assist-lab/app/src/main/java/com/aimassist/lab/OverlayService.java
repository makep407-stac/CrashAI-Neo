package com.aimassist.lab;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class OverlayService extends Service {
    private WindowManager windowManager;
    private View marker;
    private WindowManager.LayoutParams params;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        TextView view = new TextView(this);
        view.setText("＋"); view.setTextSize(40);
        view.setTextColor(Color.rgb(255, 139, 43)); view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(Color.argb(65, 10, 15, 20));
        int type = android.os.Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(100, 100, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START; params.x = 300; params.y = 500;
        view.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY; float touchX, touchY;
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) { startX=params.x; startY=params.y; touchX=event.getRawX(); touchY=event.getRawY(); return true; }
                if (event.getAction() == MotionEvent.ACTION_MOVE) { params.x=startX+(int)(event.getRawX()-touchX); params.y=startY+(int)(event.getRawY()-touchY); windowManager.updateViewLayout(view, params); return true; }
                return true;
            }
        });
        marker = view; windowManager.addView(marker, params);
    }

    @Override public void onDestroy() {
        if (windowManager != null && marker != null) { try { windowManager.removeView(marker); } catch (Exception ignored) {} }
        marker = null; super.onDestroy();
    }
}
