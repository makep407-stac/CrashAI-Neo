package sf.grh.crash;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Durable local journal of completed rounds; no artificial 500-row limit. */
public final class RoundJournal extends SQLiteOpenHelper {
    private static final String NAME = "crashai_rounds.db";
    public RoundJournal(Context context) { super(context, NAME, null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS rounds (round_id TEXT PRIMARY KEY, multiplier REAL NOT NULL, created_at INTEGER NOT NULL, synced INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS rounds_time ON rounds(created_at)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
    public synchronized boolean save(String id, double value) {
        if (id == null || id.isEmpty() || !Double.isFinite(value) || value < 1) return false;
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("round_id", id); values.put("multiplier", value);
        values.put("created_at", System.currentTimeMillis()); values.put("synced", 0);
        return getWritableDatabase().insertWithOnConflict("rounds", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }
    public synchronized int count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM rounds", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
    public synchronized void markSynced(String id) {
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("synced", 1);
        getWritableDatabase().update("rounds", values, "round_id=?", new String[]{id});
    }
}
