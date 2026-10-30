package sf.grh.crash;

import android.content.Context;
import org.json.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import java.util.concurrent.TimeUnit;

/** Read-only server status stream. Reconnects while the capture service is active. */
public final class StatusSocket {
    private final Context context;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS).build();
    private WebSocket socket;
    private volatile boolean stopped;
    private volatile boolean connected;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable reconnect = this::connect;

    public StatusSocket(Context context) { this.context = context.getApplicationContext(); }
    public boolean isConnected() { return connected; }

    public void connect() {
        if (stopped) return;
        handler.removeCallbacks(reconnect);
        Request request = new Request.Builder()
                .url("wss://crashai-neo-api.onrender.com/ws/status").build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) {
                connected = true;
                AnalysisState.update(context, "WebSocket متصل بالخادم");
            }
            @Override public void onMessage(WebSocket ws, String message) {
                try {
                    JSONObject data = new JSONObject(message);
                    if (!"status".equals(data.optString("type"))) return;
                    JSONObject estimate = data.optJSONObject("estimate");
                    int count = estimate != null ? estimate.optInt("sample_count", 0) : data.optInt("sample_count", 0);
                    AnalysisState.progress(context, count);
                    if (estimate != null && estimate.optBoolean("available", false))
                        AnalysisState.updateMultiplier(context, estimate.getDouble("estimate_multiplier"), count);
                } catch (Exception e) {
                    AnalysisState.update(context, "خطأ في بيانات WebSocket");
                }
            }
            @Override public void onFailure(WebSocket ws, Throwable t, Response response) {
                connected = false;
                if (!stopped) handler.postDelayed(reconnect, 5000);
            }
            @Override public void onClosed(WebSocket ws, int code, String reason) {
                connected = false;
                if (!stopped) handler.postDelayed(reconnect, 5000);
            }
        });
    }

    public void stop() {
        stopped = true;
        connected = false;
        handler.removeCallbacks(reconnect);
        if (socket != null) socket.close(1000, "Capture stopped");
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
