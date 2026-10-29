# CrashAI Neo — Android screen-reading prototype

This branch adds a user-consented Android MediaProjection service and on-device
Google ML Kit Latin OCR. The application never accesses 1xBet sessions or network
traffic. The user must approve screen sharing each time capture starts.

## Usage
1. Build and install the APK from this branch.
2. Open CrashAI Neo. The original main screen works without overlay permission.
3. Tap the top-left circle to request optional floating-overlay permission.
4. Tap **بدء قراءة الشاشة** and approve Android's screen capture dialog.
5. Open the Crash page; the overlay displays observed values and capture state.
6. Return to the app and tap **إيقاف المراقبة** to stop capture.

## Current limitations
- OCR uses generic full-screen text recognition; a crop/region selector for the
  specific Crash layout is still needed to avoid reading unrelated multipliers.
- Final-round detection is heuristic and can miss rounds or accept false positives.
  Do not treat collected values as verified game results without review.
- The prototype generates local capture-event IDs, not official game round IDs.
  Duplicate prevention is therefore imperfect.
- The Android app calls the Render /learn-round endpoint; the server branch must
  be deployed separately for that endpoint to exist.
- SQLite data is not durable on ephemeral Render storage; attach a persistent disk
  and set CRASHAI_DB_PATH before relying on historical retention.
- Overlay text reports observations and status, **not next-round predictions**.
- No Android build, real-device OCR evaluation, or 3-second end-to-end latency
  measurement has been completed yet.
- Screen capture may be unavailable for protected app surfaces.

## Suggested acceptance checks
- Deny overlay permission: dashboard and screen-capture controls remain usable.
- Deny screen capture: no capture starts and the dashboard remains usable.
- Start/stop capture repeatedly and confirm no leaked virtual displays.
- Compare captured values against manually verified final results on 100 rounds.
- Confirm no repeated round ingestion and measure OCR false positive rate.
- Measure p50/p95 elapsed time from final on-screen result to overlay update.
- Validate API error/offline handling and persistence across Render redeploys.

## Safety
The historical statistics, online calibration, and reverse analysis are descriptive.
They do not reveal future outcomes of independently randomized rounds.
