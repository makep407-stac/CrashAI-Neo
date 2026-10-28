package sf.grh.crash;

import android.app.Activity;
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
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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

    private static final int OVERLAY_PERMISSION_REQUEST = 701;

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

        if (Build.VERSION.SDK_INT >= 21) {
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
                "د
