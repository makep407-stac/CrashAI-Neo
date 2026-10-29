package sf.grh.crash;

import android.content.Context;
import android.content.SharedPreferences;

/** Numeric-only overlay: observed historical >=2x frequency, never a guaranteed forecast. */
public final class AnalysisState {
    private static final String PREFS = "crashai_analysis";
    private AnalysisState() {}

    public static void update(Context context, String text) {
        // Preserve non-numeric diagnostic status for the main dashboard only.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("status", text).apply();
    }

    public static void updatePercentage(Context context, double percent, int sampleCount) {
        if (Double.isNaN(percent) || Double.isInfinite(percent)
                || percent < 0 || percent > 100 || sampleCount < 30) return;
        String numeric = String.format(java.util.Locale.US, "%.0f%%", percent);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("display", numeric)
                .putInt("sample_count", sampleCount).apply();
    }

    public static String current(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("display", "—");
    }

    public static String status(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString("status", "بانتظار النتائج") +
                " • نسبة تاريخية ≥2x، ليست تنبؤًا مضمونًا • عدد الجولات: " +
                prefs.getInt("sample_count", 0);
    }
}
