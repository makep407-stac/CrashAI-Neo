package sf.grh.crash;
import android.content.Context;
import android.content.SharedPreferences;
public final class AnalysisState {
 private static final String PREFS="crashai_analysis";
 private AnalysisState() {}
 public static void update(Context c,String status) {
  c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("status",status).apply();
 }
 public static void updateMultiplier(Context c,double value,int count) {
  if(Double.isNaN(value)||Double.isInfinite(value)||value<1||count<30)return;
  String number=String.format(java.util.Locale.US,"%.2f",value);
  c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("display",number).putInt("sample_count",count).apply();
 }
 public static String current(Context c) {
  return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("display","");
 }
 public static String status(Context c) {
  SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
  return p.getString("status","بانتظار النتائج")+" • تقدير تاريخي للمضاعف، ليس تنبؤًا مثبتًا • عدد الجولات: "+p.getInt("sample_count",0);
 }
}