package com.crashai.neo.integration

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Receives confirmed completed rounds from CrashAI-Neo.
 * This stream carries actual outcomes only; it is not a future-round predictor.
 */
class LiveResultSocket(
    private val onActualResult: (roundId: String, multiplier: Double, occurredAt: String) -> Unit,
    private val onState: (String) -> Unit = {}
) {
    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private var socket: WebSocket? = null
    private var retryMs = 2_000L
    @Volatile private var stopped = false

    fun connect(url: String = "wss://crashai-neo-api.onrender.com/ws/live-results") {
        stopped = false
        open(url)
    }

    private fun open(url: String) {
        if (stopped) return
        onState("connecting")
        socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    retryMs = 2_000L
                    main.post { onState("connected") }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val event = JSONObject(text)
                        if (event.optString("type") != "live_result" ||
                            !event.optBoolean("confirmed", false)) return
                        val id = event.optString("round_id")
                        val value = event.optDouble("actual_multiplier", Double.NaN)
                        if (id.isBlank() || value.isNaN() || value < 1.0) return
                        val at = event.optString("occurred_at")
                        main.post { onActualResult(id, value, at) }
                    } catch (_: Exception) {
                        // Ignore malformed events; never display unvalidated values.
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    scheduleReconnect(url)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    main.post { onState("disconnected") }
                    scheduleReconnect(url)
                }
            }
        )
    }

    private fun scheduleReconnect(url: String) {
        if (stopped) return
        main.post { onState("reconnecting") }
        val delay = retryMs
        retryMs = min(retryMs * 2, 60_000L)
        main.postDelayed({ if (!stopped) open(url) }, delay)
    }

    fun disconnect() {
        stopped = true
        socket?.close(1000, "client_closed")
        socket = null
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        onState("disconnected")
    }
}
