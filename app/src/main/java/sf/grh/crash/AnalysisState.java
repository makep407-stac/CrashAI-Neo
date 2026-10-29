package sf.grh.crash;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public final class AnalysisState {
    public static final String ACTION_UPDATED = "sf.grh.crash.ANALYSIS_UPDATED";
    private static final String PREFS = "crashai_analysis";
    private AnalysisState() {}
    public static void update(Context context, String text) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("display", text).apply();
        Intent intent = new Intent(ACTION_UPDATED);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
    public static String current(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString("display", "بانتظار القراءة");
    }
}
