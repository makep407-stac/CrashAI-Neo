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
  if(Double.isNaN(value)||Double.isInfinite(value)||value<1||count<20)return;
  String number=String.format(java.util.Locale.US,"%.2f",value);
  c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("display",number).putInt("sample_count",count).apply();
 }
 public static void recorded(Context c,String value) {
  c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("last_recorded","تم تسجيل " + value + "x").apply();
 }
 public static String lastRecorded(Context c) {
  return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("last_recorded","بانتظار تسجيل الجولات");
 }
 public static void progress(Context c,int count) {
  c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putInt("sample_count",Math.max(c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getInt("sample_count",0),Math.max(0,count))).apply();
 }
 public static String current(Context c) {
  SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
  String value=p.getString("display","");
  int local = 0;
  try (RoundJournal journal = new RoundJournal(c)) { local = journal.count(); } catch (Exception ignored) {}
  return value.isEmpty() ? local+"/20" : value+"x";
 }
 public static String status(Context c) {
  SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
  int local = 0;
  try (RoundJournal journal = new RoundJournal(c)) { local = journal.count(); } catch (Exception ignored) {}
  return p.getString("status","جمع البيانات: يلزم حفظ 20 جولة أولاً")+" • جولات الهاتف: "+local+" • جولات الخادم: "+p.getInt("sample_count",0)+" • تقدير تاريخي وليس تنبؤًا مثبتًا";
 }
}