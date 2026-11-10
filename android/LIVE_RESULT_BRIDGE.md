# Live Result Bridge — Android sphere integration

## What this adds
- `GET /ws/live-results` is a WebSocket stream of **completed, validated outcomes**.
- The stream replays the latest 20 stored outcomes after a phone reconnects.
- New accepted outcomes are broadcast immediately after `POST /external-feed/import`.
- Duplicate round IDs are ignored by SQLite and are not rebroadcast.
- The native Kotlin adapter below rejects malformed values and unconfirmed messages.
- The sphere shows the estimate and actual result in separate, explicit rows.

## Add to the Android app
1. Copy `LiveResultSocket.kt` and `LiveResultSphereView.kt` into the app's Kotlin source package, or change their package declaration to match the app.
2. Add OkHttp to the app module:
   ```gradle
   implementation("com.squareup.okhttp3:okhttp:4.12.0")
   ```
3. Add the custom view to the existing illuminated sphere layout:
   ```xml
   <com.crashai.neo.integration.LiveResultSphereView
       android:id="@+id/liveResultSphere"
       android:layout_width="280dp"
       android:layout_height="280dp" />
   ```
4. In the Activity/Fragment, connect the stream and update the view:
   ```kotlin
   val sphere = findViewById<LiveResultSphereView>(R.id.liveResultSphere)
   val live = LiveResultSocket(
       onActualResult = { roundId, multiplier, _ ->
           sphere.setActual(multiplier)
       },
       onState = { state -> sphere.setConnectionState(state) }
   )
   live.connect()
   // Supply the estimate from the app's existing /next-estimate response:
   sphere.setEstimate(estimateMultiplier)
   // Call live.disconnect() from onDestroy().
   ```

## Important deployment requirements
- Configure `CRASHAI_INGEST_KEY` on the CrashAI-Neo Render service with a random value of at least 32 characters.
- The authorized collector must POST completed rounds to `/external-feed/import` with header `X-CrashAI-Ingest-Key`.
- Keep all provider/session credentials out of the Android APK and GitHub.
- Render's default filesystem is ephemeral. Configure a persistent disk and set `CRASHAI_DB_PATH` to a path on that disk if round history must survive deploys.
- The current repository does not contain the actual Android app project/Activity. These are integration classes and instructions; the classes still need to be copied into the app's real source tree and the APK rebuilt.

## Event contract
```json
{
  "type": "live_result",
  "round_id": "provider-round-id",
  "actual_multiplier": 2.45,
  "source": "authorized_external_feed",
  "confirmed": true,
  "occurred_at": "2026-11-10T12:00:00+00:00",
  "prediction": null
}
```

The stream displays the final outcome only after it has been received. It cannot establish a 100% accurate forecast before a round ends.
